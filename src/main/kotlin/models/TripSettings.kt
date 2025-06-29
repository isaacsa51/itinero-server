package com.serranoie.server.models

import kotlinx.serialization.Serializable

@Serializable
data class TripInfoSettings(
    val id: Int,
    val accommodationName: String,
    val checkIn: String,
    val checkOut: String,
    val phone: String,
    val reservationCode: String?,
    val location: Location,
    val additionalLocationInfo: String?,
    val generalInfo: String?
)

@Serializable
data class GroupSettings(
    val id: Int,
    val groupCode: String,
    val tripName: String,
    val startDate: String,
    val endDate: String,
    val summary: String,
    val members: List<TripMember>,
    val isOwner: Boolean
)

@Serializable
data class TripMember(
    val id: Int,
    val name: String,
    val email: String,
    val status: String // "OWNER", "ACCEPTED", "PENDING"
)

@Serializable
data class UpdateTripInfoRequest(
    val groupName: String,
    val destination: String,
    val startDate: String,
    val endDate: String,
    val summary: String,
    val accommodation: Accommodation,
    val reservationCode: String?,
    val extraInfo: String?,
    val additionalInfo: String?
)

@Serializable
data class UpdateGroupSettingsRequest(
    val tripName: String,
    val startDate: String,
    val endDate: String,
    val summary: String
)

@Serializable
data class MemberActionResponse(
    val success: Boolean,
    val message: String
)

@Serializable
data class InviteMemberRequest(
    val email: String
)

@Serializable
data class JoinGroupRequest(
    val groupCode: String
)
