package com.serranoie.server.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateTripRequest(
    val destination: String,
    val startDate: String,  // ISO 8601 format: yyyy-MM-dd
    val endDate: String,    // ISO 8601 format: yyyy-MM-dd
    val summary: String,
    val accommodation: Accommodation,
    val reservationCode: String? = null,
    val extraInfo: String? = null,
    val additionalInfo: String? = null
)