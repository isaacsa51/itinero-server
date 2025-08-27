package com.serranoie.server.repository

import com.serranoie.server.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

object TripMembers : Table("trip_members") {
    val tripId = integer("trip_id").references(Trips.id)
    val userId = integer("user_id").references(Users.id)
    val isAccepted = bool("is_accepted").default(false)
    val isPending = bool("is_pending").default(true)

    override val primaryKey = PrimaryKey(tripId, userId)
}

fun addMemberToTrip(userId: Int, tripId: Int) = transaction {
    val exists = TripMembers.selectAll()
        .where { (TripMembers.userId eq userId) and (TripMembers.tripId eq tripId) }
        .count() > 0

    if (!exists) {
        TripMembers.insert {
            it[TripMembers.userId] = userId
            it[TripMembers.tripId] = tripId
            it[isAccepted] = false
            it[isPending] = true
        }
        updateTotalMembers(tripId)
        return@transaction true
    }
    return@transaction false
}

fun inviteMemberByEmail(email: String, tripId: Int): Boolean = transaction {
    val user = findUserByEmail(email) ?: return@transaction false

    val alreadyMember = TripMembers.selectAll()
        .where { (TripMembers.userId eq user.id) and (TripMembers.tripId eq tripId) }
        .count() > 0

    if (alreadyMember) {
        return@transaction false
    }

    TripMembers.insert {
        it[userId] = user.id
        it[TripMembers.tripId] = tripId
        it[isAccepted] = false
        it[isPending] = true
    }

    updateTotalMembers(tripId)

    return@transaction true
}

fun joinGroupByCode(userId: Int, groupCode: String): Int? = transaction {
    val trip = Trips.selectAll()
        .where { Trips.groupCode eq groupCode }
        .singleOrNull() ?: return@transaction null

    val tripId = trip[Trips.id]

    val alreadyMember = TripMembers.selectAll()
        .where { (TripMembers.userId eq userId) and (TripMembers.tripId eq tripId) }
        .count() > 0

    if (alreadyMember) {
        return@transaction tripId
    }

    TripMembers.insert {
        it[TripMembers.userId] = userId
        it[TripMembers.tripId] = tripId
        it[isAccepted] = false
        it[isPending] = true
    }

    updateTotalMembers(tripId)

    return@transaction tripId
}

fun getTripMembers(tripId: Int): List<TripMember> = transaction {
    val trip = Trips.selectAll().where { Trips.id eq tripId }.singleOrNull()
    val ownerId = trip?.get(Trips.ownerId)

    (TripMembers innerJoin Users)
        .selectAll()
        .where { TripMembers.tripId eq tripId }
        .map {
            val userId = it[Users.id]
            val status = when {
                userId == ownerId -> "OWNER"
                it[TripMembers.isAccepted] -> "ACCEPTED"
                it[TripMembers.isPending] -> "PENDING"
                else -> "UNKNOWN"
            }

            TripMember(
                id = userId,
                name = "${it[Users.name]} ${it[Users.surname]}",
                email = it[Users.email],
                status = status
            )
        }
}

fun getPendingMembers(tripId: Int): List<TripMember> = transaction {
    val trip = Trips.selectAll().where { Trips.id eq tripId }.singleOrNull()
    val ownerId = trip?.get(Trips.ownerId)

    (TripMembers innerJoin Users)
        .selectAll()
        .where { (TripMembers.tripId eq tripId) and (TripMembers.isPending eq true) }
        .map {
            val userId = it[Users.id]
            val status = when {
                userId == ownerId -> "OWNER"
                it[TripMembers.isAccepted] -> "ACCEPTED"
                it[TripMembers.isPending] -> "PENDING"
                else -> "UNKNOWN"
            }

            TripMember(
                id = userId,
                name = "${it[Users.name]} ${it[Users.surname]}",
                email = it[Users.email],
                status = status
            )
        }
}

fun updateTotalMembers(tripId: Int) = transaction {
    val acceptedMembersCount = TripMembers.selectAll()
        .where { (TripMembers.tripId eq tripId) and (TripMembers.isAccepted eq true) }
        .count()

    Trips.update({ Trips.id eq tripId }) {
        it[totalMembers] = acceptedMembersCount.toInt()
    }
}

fun acceptTripMember(userId: Int, tripId: Int): Boolean = transaction {
    val updated = TripMembers.update({ (TripMembers.userId eq userId) and (TripMembers.tripId eq tripId) }) {
        it[isAccepted] = true
        it[isPending] = false
    } > 0

    if (updated) {
        updateTotalMembers(tripId)
    }

    updated
}

fun rejectTripMember(userId: Int, tripId: Int): Boolean = transaction {
    val deleted = TripMembers.deleteWhere { (TripMembers.userId eq userId) and (TripMembers.tripId eq tripId) } > 0

    if (deleted) {
        updateTotalMembers(tripId)
    }

    deleted
}

fun isUserTripOwner(userId: Int, tripId: Int): Boolean = transaction {
    Trips.selectAll()
        .where { (Trips.id eq tripId) and (Trips.ownerId eq userId) }
        .count() > 0
}

fun isTripMember(userId: Int, tripId: Int): Boolean = transaction {
    TripMembers.selectAll()
        .where { (TripMembers.userId eq userId) and (TripMembers.tripId eq tripId) and (TripMembers.isAccepted eq true) }
        .count() > 0
}

fun isUserPendingMember(userId: Int, tripId: Int): Boolean = transaction {
    TripMembers.selectAll()
        .where { (TripMembers.userId eq userId) and (TripMembers.tripId eq tripId) and (TripMembers.isPending eq true) }
        .count() > 0
}

fun leaveTrip(userId: Int, tripId: Int): Boolean = transaction {
    val deleted = TripMembers.deleteWhere { (TripMembers.userId eq userId) and (TripMembers.tripId eq tripId) } > 0

    if (deleted) {
        updateTotalMembers(tripId)
    }

    deleted
}

fun deleteTrip(tripId: Int) = transaction {
    TripMembers.deleteWhere { TripMembers.tripId eq tripId }

    Trips.deleteWhere { id eq tripId }
}

fun findMemberTrips(userId: Int): List<Trip> = transaction {
    (TripMembers innerJoin Trips)
        .selectAll()
        .where { (TripMembers.userId eq userId) and (TripMembers.isAccepted eq true) }
        .map {
            Trip(
                id = it[Trips.id],
                destination = it[Trips.destination],
                groupName = it[Trips.groupName],
                startDate = it[Trips.startDate],
                endDate = it[Trips.endDate],
                summary = it[Trips.summary],
                totalMembers = it[Trips.totalMembers],
                accommodation = Accommodation(
                    name = it[Trips.accommodationName],
                    phone = it[Trips.accommodationPhone],
                    checkIn = it[Trips.checkIn],
                    checkOut = it[Trips.checkOut],
                    latitude = it[Trips.accommodationLatitude],
                    longitude = it[Trips.accommodationLongitude],
                    reservationCode = it[Trips.reservationCode],
                    extraInfo = it[Trips.extraInfo]
                ),
                groupCode = it[Trips.groupCode],
                ownerId = it[Trips.ownerId]
            )
        }
}

fun getTripInfoSettings(tripId: Int): TripInfoSettings? = transaction {
    val trip = Trips.selectAll()
        .where { Trips.id eq tripId }
        .singleOrNull() ?: return@transaction null

    TripInfoSettings(
        id = trip[Trips.id],
        accommodationName = trip[Trips.accommodationName],
        checkIn = trip[Trips.checkIn],
        checkOut = trip[Trips.checkOut],
        phone = trip[Trips.accommodationPhone],
        reservationCode = trip[Trips.reservationCode],
        latitude = trip[Trips.accommodationLatitude],
        longitude = trip[Trips.accommodationLongitude],
        extraInfo = trip[Trips.extraInfo]
    )
}

fun transferTripOwnership(tripId: Int, newOwnerId: Int): Boolean = transaction {
    val updated = Trips.update({ Trips.id eq tripId }) {
        it[ownerId] = newOwnerId
    } > 0

    updated
}

fun updateTripInfo(groupCode: String, request: UpdateTripInfoRequest): Boolean = transaction {
    Trips.update({ Trips.groupCode eq groupCode }) {
        it[groupName] = request.groupName
        it[destination] = request.destination
        it[startDate] = request.startDate
        it[endDate] = request.endDate
        it[summary] = request.summary
        it[accommodationName] = request.accommodation.name
        it[accommodationPhone] = request.accommodation.phone
        it[checkIn] = request.accommodation.checkIn
        it[checkOut] = request.accommodation.checkOut
        it[accommodationLatitude] = request.accommodation.latitude
        it[accommodationLongitude] = request.accommodation.longitude
        it[reservationCode] = request.reservationCode
        it[extraInfo] = request.extraInfo
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

fun findTripByGroupCode(groupCode: String): Int? = transaction {
    val result = Trips.selectAll()
        .where { Trips.groupCode eq groupCode }
        .map { it[Trips.id] }
        .singleOrNull()

    return@transaction result
}
