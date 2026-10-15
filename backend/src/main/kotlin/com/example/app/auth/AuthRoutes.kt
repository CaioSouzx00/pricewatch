package com.example.app.auth

import com.example.app.dto.UserCreateRequest
import com.example.app.dto.UserResponse
import com.example.app.repository.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class AuthResponse(val token: String, val user: UserResponse)

private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

class SimpleRateLimiter(
    private val maxRequests: Int = 10,
    private val windowMs: Long = 60_000L,
) {
    private data class Window(val timestamp: Long, val count: AtomicInteger)
    private val clients = ConcurrentHashMap<String, Window>()

    fun allow(key: String): Boolean {
        val now = System.currentTimeMillis()
        val window = clients.compute(key) { _, current ->
            if (current == null || now - current.timestamp > windowMs) {
                Window(now, AtomicInteger(1))
            } else {
                current.count.incrementAndGet()
                current
            }
        }!!
        if (clients.size > 5_000) {
            clients.entries.removeIf { now - it.value.timestamp > windowMs }
        }
        return window.count.get() <= maxRequests
    }
}

private val authRateLimiter = SimpleRateLimiter(maxRequests = 10, windowMs = 60_000L)

private fun ApplicationCall.clientIp(): String =
    request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
        ?: request.local.remoteHost

private fun UserCreateRequest.validate() {
    if (!EMAIL_REGEX.matches(email.trim())) throw ApiException(HttpStatusCode.BadRequest, "E-mail inválido")
    if (name.isBlank() || name.length > 120) throw ApiException(HttpStatusCode.BadRequest, "Nome inválido")
    if (password.length !in 8..72) {
        throw ApiException(HttpStatusCode.BadRequest, "A senha deve ter entre 8 e 72 caracteres")
    }
}

fun Route.authRoutes(users: UserRepository, jwt: JwtConfig) {
    route("/auth") {
        post("/register") {
            val ip = call.clientIp()
            if (!authRateLimiter.allow("reg:$ip")) {
                throw ApiException(HttpStatusCode.TooManyRequests, "Muitas tentativas de cadastro. Tente novamente em 1 minuto.")
            }
            val raw = call.receive<UserCreateRequest>()
            val request = raw.copy(
                email = raw.email.trim().lowercase(),
                name = raw.name.trim(),
            )
            request.validate()
            if (users.findByEmail(request.email) != null) {
                throw ApiException(HttpStatusCode.Conflict, "E-mail já cadastrado")
            }
            val user = users.create(request, PasswordHasher.hash(request.password))
            call.respond(HttpStatusCode.Created, AuthResponse(jwt.generateToken(user.id, user.role), user))
        }

        post("/login") {
            val ip = call.clientIp()
            if (!authRateLimiter.allow("login:$ip")) {
                throw ApiException(HttpStatusCode.TooManyRequests, "Muitas tentativas de login. Tente novamente em 1 minuto.")
            }
            val request = call.receive<LoginRequest>()
            val normalizedEmail = request.email.trim().lowercase()
            val invalid = ApiException(HttpStatusCode.Unauthorized, "Credenciais inválidas")
            val hash = users.findPasswordHash(normalizedEmail) ?: throw invalid
            if (!PasswordHasher.verify(request.password, hash)) throw invalid
            val user = users.findByEmail(normalizedEmail) ?: throw invalid
            call.respond(AuthResponse(jwt.generateToken(user.id, user.role), user))
        }

        authenticate(JWT_AUTH) {
            get("/me") {
                val user = users.findById(call.userId())
                    ?: throw ApiException(HttpStatusCode.Unauthorized, "Usuário não encontrado")
                call.respond(user)
            }
        }
    }
}
