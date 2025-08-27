package com.serranoie.server.models

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Int,
    val name: String,
    val surname: String,
    val phone: String,
    val email: String,
    val passwordHash: String,
    val plannedTrips: List<Int> = emptyList(),
)

@Serializable
data class RegisterRequest(
    val name: String,
    val surname: String,
    val phone: String,
    val email: String,
    val password: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class DeleteAccountRequest(
    val password: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val userId: Int,
    val name: String,
    val lastName: String
)