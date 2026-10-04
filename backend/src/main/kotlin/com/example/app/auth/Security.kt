package com.example.app.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import org.mindrot.jbcrypt.BCrypt

@Serializable
data class ErrorResponse(val error: String)

class ApiException(val status: HttpStatusCode, override val message: String) : RuntimeException(message)

object PasswordHasher {
    fun hash(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt(12))

    fun verify(password: String, hash: String): Boolean =
        runCatching { BCrypt.checkpw(password, hash) }.getOrDefault(false)
}

const val JWT_AUTH = "auth-jwt"

fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<ApiException> { call, e ->
            call.respond(e.status, ErrorResponse(e.message))
        }
        exception<io.ktor.server.plugins.BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Requisição inválida"))
        }
        exception<Throwable> { call, e ->
            call.application.environment.log.error("Erro não tratado", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Erro interno"))
        }
    }
}

fun Application.configureSecurity(jwtConfig: JwtConfig) {
    install(Authentication) {
        jwt(JWT_AUTH) {
            realm = jwtConfig.realm
            verifier(jwtConfig.verifier)
            validate { credential ->
                if (credential.payload.subject?.toLongOrNull() != null) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Token inválido ou ausente"))
            }
        }
    }
}

/** Retorna o id do usuário autenticado. Use somente dentro de `authenticate(JWT_AUTH)`. */
fun ApplicationCall.userId(): Long =
    principal<JWTPrincipal>()?.payload?.subject?.toLongOrNull()
        ?: throw ApiException(HttpStatusCode.Unauthorized, "Não autenticado")
