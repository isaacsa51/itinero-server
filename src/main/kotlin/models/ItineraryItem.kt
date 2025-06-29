package com.serranoie.server.models

import kotlinx.serialization.Serializable

@Serializable
data class ItineraryItem(
    val id: Int,
    val groupCode: String,
    val name: String,
    val description: String,
    val date: String,
    val time: String,
    val location: String,
    val isCompleted: Boolean = false
)

@Serializable
data class CreateItineraryItemRequest(
    val name: String,
    val description: String,
    val date: String,
    val time: String,
    val location: String,
    val isCompleted: Boolean = false
)

@Serializable
data class UpdateItineraryItemRequest(
    val name: String? = null,
    val description: String? = null,
    val date: String? = null,
    val time: String? = null,
    val location: String? = null,
    val isCompleted: Boolean? = null
)
