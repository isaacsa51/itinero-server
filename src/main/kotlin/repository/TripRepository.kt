package com.serranoie.server.repository

import com.serranoie.server.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

fun getTripInfoSettings(tripId: Int): TripInfoSettings? = transaction {
    Trips.selectAll()
        .where { Trips.id eq tripId }
        .map {
            TripInfoSettings(
                id = it[Trips.id],
                accommodationName = it[Trips.accommodationName],
                checkIn = it[Trips.checkIn],
                checkOut = it[Trips.checkOut],
                phone = it[Trips.accommodationPhone],
                reservationCode = it[Trips.reservationCode],
                location = Location(
                    name = it[Trips.locationName],
                    latitude = it[Trips.latitude],
                    longitude = it[Trips.longitude]
                ),
                additionalLocationInfo = it[Trips.extraInfo],
                generalInfo = it[Trips.additionalInfo]
            )
        }.singleOrNull()
}

fun updateTripInfo(tripId: Int, request: UpdateTripInfoRequest): Boolean = transaction {
    Trips.update({ Trips.id eq tripId }) {
        it[accommodationName] = request.accommodationName
        it[checkIn] = request.checkIn
        it[checkOut] = request.checkOut
        it[accommodationPhone] = request.phone
        it[reservationCode] = request.reservationCode
        it[locationName] = request.locationName
        it[latitude] = request.latitude
        it[longitude] = request.longitude
        it[extraInfo] = request.additionalLocationInfo
        it[additionalInfo] = request.generalInfo
    } > 0
}

fun getGroupSettings(tripId: Int, currentUserId: Int): GroupSettings? = transaction {
    val trip = Trips.selectAll()
        .where { Trips.id eq tripId }
        .singleOrNull() ?: return@transaction null

    val members = getTripMembers(tripId)

    GroupSettings(
        id = trip[Trips.id],
        groupCode = trip[Trips.groupCode],
        tripName = trip[Trips.destination],
        startDate = trip[Trips.startDate],
        endDate = trip[Trips.endDate],
        summary = trip[Trips.summary],
        members = members,
        isOwner = trip[Trips.ownerId] == currentUserId
    )
}

fun updateGroupSettings(tripId: Int, request: UpdateGroupSettingsRequest): Boolean = transaction {
    Trips.update({ Trips.id eq tripId }) {
        it[destination] = request.tripName
        it[startDate] = request.startDate
        it[endDate] = request.endDate
        it[summary] = request.summary
    } > 0
}