package com.example.app.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

class JwtConfig(
    secret: String,
    val issuer: String,
    val audience: String,
    private val expiresInMinutes: Long = 60,
) {
    val realm = "mvp"
    private val algorithm: Algorithm = Algorithm.HMAC256(secret)

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

    fun generateToken(userId: Long): String = JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withSubject(userId.toString())
        .withExpiresAt(Date(System.currentTimeMillis() + expiresInMinutes * 60_000))
        .sign(algorithm)

    companion object {
        fun fromEnv(): JwtConfig {
            val secret = System.getenv("JWT_SECRET")
                ?: error("JWT_SECRET não definido. Veja .env.example")
            require(secret.length >= 32) { "JWT_SECRET deve ter ao menos 32 caracteres" }
            return JwtConfig(
                secret = secret,
                issuer = System.getenv("JWT_ISSUER") ?: "mvp-backend",
                audience = System.getenv("JWT_AUDIENCE") ?: "mvp-users",
                expiresInMinutes = System.getenv("JWT_EXPIRES_MINUTES")?.toLongOrNull() ?: 60,
            )
        }
    }
}
