package com.serranoie.server.models

import kotlinx.serialization.Serializable

@Serializable
data class Trip(
    val id: Int,
    val destination: String,
    val startDate: String,      // ISO 8601 (yyyy-MM-dd)
    val endDate: String,        // ISO 8601
    val totalDays: Int,
    val summary: String,        // Markdown
    val totalMembers: Int,
    val travelDirection: TravelDirection,
    val hasPendingActions: Boolean,
    val accommodation: Accommodation
)

@Serializable
data class Accommodation(
    val name: String,
    val phone: String,
    val checkIn: String,        // ISO 8601 DateTime
    val checkOut: String,       // ISO 8601 DateTime
    val location: Location
)

@Serializable
data class Location(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

@Serializable
enum class TravelDirection {
    OUTBOUND, RETURN
}