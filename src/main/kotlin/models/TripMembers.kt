package com.serranoie.server.models

import kotlinx.serialization.Serializable

@Serializable
data class TripMembers(
    val userId: String,
    val tripId: String,
    val isAccepted: Boolean,
)
