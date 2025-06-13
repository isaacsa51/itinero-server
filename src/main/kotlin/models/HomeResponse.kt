package com.serranoie.server.models

import kotlinx.serialization.Serializable

@Serializable
data class HomeResponse(
    val currentTrip: Trip,
    val allTrips: List<Trip>
)