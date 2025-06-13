package com.serranoie.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.text.Charsets.UTF_8

private const val secret = "your-secret-key"
private const val issuer = "ktor-auth"
private const val validityInMs = 36_000_00 * 10 // 10 hours

fun hashPassword(password: String): String {
    val hmacKey = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(hmacKey)
    return mac.doFinal(password.toByteArray(UTF_8)).joinToString("") { "%02x".format(it) }
}

fun generateToken(username: String): String {
    return JWT.create()
        .withIssuer(issuer)
        .withClaim("username", username)
        .withExpiresAt(Date(System.currentTimeMillis() + validityInMs))
        .sign(Algorithm.HMAC256(secret))
}