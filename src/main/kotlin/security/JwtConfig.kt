package com.serranoie.server.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.*

object JwtConfig {
    private const val secret = "super_secret_key"
    private const val issuer = "itinero-server"
    private const val validityInMs = 36_000_00 * 10 // 10 hours

    private val algorithm = Algorithm.HMAC512(secret)

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .build()

    fun generateToken(email: String): String =
        JWT.create()
            .withIssuer(issuer)
            .withClaim("email", email)
            .withExpiresAt(Date(System.currentTimeMillis() + validityInMs))
            .sign(algorithm)
}