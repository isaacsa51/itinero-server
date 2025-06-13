package com.serranoie.server.models

import kotlinx.serialization.Serializable

@Serializable
data class Trip(
    val id: Int,
    val destination: String,
    val startDate: String,
    val endDate: String,
    val summary: String,
    val totalMembers: Int,
    val accommodation: Accommodation,
    val reservationCode: String? = null,
    val extraInfo: String? = null,
    val additionalInfo: String? = null,
    val groupCode: String,
    val groupName: String,
    val ownerId: Int,
)

@Serializable
data class Accommodation(
    val name: String,
    val phone: String,
    val checkIn: String,
    val checkOut: String,
    val location: String,
    val mapUri: String
)

@Serializable
data class Location(
    val name: String,
    val latitude: Double,
    val longitude: Double
)
