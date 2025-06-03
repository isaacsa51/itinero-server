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

    return@transaction tripId
}

fun getTripMembers(tripId: Int): List<TripMember> = transaction {
    (TripMembers innerJoin Users)
        .selectAll()
        .where { TripMembers.tripId eq tripId }
        .map {
            TripMember(
                id = it[Users.id],
                name = "${it[Users.name]} ${it[Users.surname]}",
                email = it[Users.email],
                isAccepted = it[TripMembers.isAccepted],
                isPending = it[TripMembers.isPending]
            )
        }
}

fun getPendingMembers(tripId: Int): List<TripMember> = transaction {
    (TripMembers innerJoin Users)
        .selectAll()
        .where { (TripMembers.tripId eq tripId) and (TripMembers.isPending eq true) }
        .map {
            TripMember(
                id = it[Users.id],
                name = "${it[Users.name]} ${it[Users.surname]}",
                email = it[Users.email],
                isAccepted = it[TripMembers.isAccepted],
                isPending = it[TripMembers.isPending]
            )
        }
}

fun acceptTripMember(userId: Int, tripId: Int): Boolean = transaction {
    TripMembers.update({ (TripMembers.userId eq userId) and (TripMembers.tripId eq tripId) }) {
        it[isAccepted] = true
        it[isPending] = false
    } > 0
}

fun rejectTripMember(userId: Int, tripId: Int): Boolean = transaction {
    TripMembers.deleteWhere { (TripMembers.userId eq userId) and (TripMembers.tripId eq tripId) } > 0
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

fun leaveTrip(userId: Int, tripId: Int): Boolean = transaction {
    TripMembers.deleteWhere { (TripMembers.userId eq userId) and (TripMembers.tripId eq tripId) } > 0
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
                startDate = it[Trips.startDate],
                endDate = it[Trips.endDate],
                summary = it[Trips.summary],
                totalMembers = it[Trips.totalMembers],
                travelDirection = TravelDirection.valueOf(it[Trips.travelDirection]),
                hasPendingActions = it[Trips.hasPendingActions],
                accommodation = Accommodation(
                    name = it[Trips.accommodationName],
                    phone = it[Trips.accommodationPhone],
                    checkIn = it[Trips.checkIn],
                    checkOut = it[Trips.checkOut],
                    location = it[Trips.location],
                    mapUri = it[Trips.mapUri]
                ),
                reservationCode = it[Trips.reservationCode],
                extraInfo = it[Trips.extraInfo],
                additionalInfo = it[Trips.additionalInfo],
                groupCode = it[Trips.groupCode],
                ownerId = it[Trips.ownerId]
            )
        }
}

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
                    name = it[Trips.location],
                    latitude = 0.0,
                    longitude = 0.0
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
        it[location] = request.locationName
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

fun findTripByGroupCode(groupCode: String): Int? = transaction {
    Trips.selectAll()
        .where { Trips.groupCode eq groupCode }
        .map { it[Trips.id] }
        .singleOrNull()
}
