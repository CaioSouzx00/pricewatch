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

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class AuthResponse(val token: String, val user: UserResponse)

private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

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
            val request = call.receive<UserCreateRequest>()
            request.validate()
            if (users.findByEmail(request.email) != null) {
                throw ApiException(HttpStatusCode.Conflict, "E-mail já cadastrado")
            }
            val user = users.create(request, PasswordHasher.hash(request.password))
            call.respond(HttpStatusCode.Created, AuthResponse(jwt.generateToken(user.id), user))
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            val invalid = ApiException(HttpStatusCode.Unauthorized, "Credenciais inválidas")
            val hash = users.findPasswordHash(request.email) ?: throw invalid
            if (!PasswordHasher.verify(request.password, hash)) throw invalid
            val user = users.findByEmail(request.email) ?: throw invalid
            call.respond(AuthResponse(jwt.generateToken(user.id), user))
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
