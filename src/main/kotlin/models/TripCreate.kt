package com.serranoie.server.models

import kotlinx.serialization.Serializable

@Serializable
data class CreateTripRequest(
    val destination: String,
    val startDate: String,
    val endDate: String,
    val summary: String,
    val accommodation: Accommodation,
    val reservationCode: String,
    val extraInfo: String,
    val additionalInfo: String
)