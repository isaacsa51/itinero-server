package com.serranoie.server.repository

import com.serranoie.server.models.Accommodation
import com.serranoie.server.models.Trip
import com.serranoie.server.repository.createChatGroup
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.ByteBuffer
import java.security.MessageDigest

object Trips : Table() {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id)
    val destination = varchar("destination", 100)
    val startDate = varchar("start_date", 10) // ISO 8601 date format: yyyy-MM-dd
    val endDate = varchar("end_date", 10)
    val summary = text("summary")
    val totalMembers = integer("total_members")
    val accommodationName = varchar("accommodation_name", 100)
    val accommodationPhone = varchar("accommodation_phone", 20)
    val checkIn = varchar("check_in", 19)
    val checkOut = varchar("check_out", 19)
    val accommodationLatitude = double("accommodation_latitude")
    val accommodationLongitude = double("accommodation_longitude")
    val reservationCode = varchar("reservation_code", 50).nullable()
    val extraInfo = text("extra_info").nullable()
    val groupCode = varchar("group_code", 20).uniqueIndex()
    val groupName = varchar("group_name", 100)
    val ownerId = integer("owner_id").references(Users.id)

    override val primaryKey = PrimaryKey(id)
}

fun getTripById(tripId: Int): Trip? = transaction {
    Trips.selectAll().where { Trips.id eq tripId }.map { row ->
        Trip(
            id = row[Trips.id],
            destination = row[Trips.destination],
            startDate = row[Trips.startDate],
            endDate = row[Trips.endDate],
            summary = row[Trips.summary],
            totalMembers = row[Trips.totalMembers],
            accommodation = Accommodation(
                name = row[Trips.accommodationName],
                phone = row[Trips.accommodationPhone],
                checkIn = row[Trips.checkIn],
                checkOut = row[Trips.checkOut],
                latitude = row[Trips.accommodationLatitude],
                longitude = row[Trips.accommodationLongitude],
                reservationCode = row[Trips.reservationCode],
                extraInfo = row[Trips.extraInfo]
            ),
            groupCode = row[Trips.groupCode],
            groupName = row[Trips.groupName],
            ownerId = row[Trips.ownerId]
        )
    }.singleOrNull()
}

fun getTripsForUser(userId: Int): List<Trip> = transaction {
    (TripMembers innerJoin Trips).selectAll().where {
            TripMembers.userId eq userId and (TripMembers.isAccepted eq true)
        }.map { row ->
            Trip(
                id = row[Trips.id],
                destination = row[Trips.destination],
                startDate = row[Trips.startDate],
                endDate = row[Trips.endDate],
                summary = row[Trips.summary],
                totalMembers = row[Trips.totalMembers],
                accommodation = Accommodation(
                    name = row[Trips.accommodationName],
                    phone = row[Trips.accommodationPhone],
                    checkIn = row[Trips.checkIn],
                    checkOut = row[Trips.checkOut],
                    latitude = row[Trips.accommodationLatitude],
                    longitude = row[Trips.accommodationLongitude],
                    reservationCode = row[Trips.reservationCode],
                    extraInfo = row[Trips.extraInfo]
                ),
                groupCode = row[Trips.groupCode],
                groupName = row[Trips.groupName],
                ownerId = row[Trips.ownerId]
            )
        }
}

fun generateUniqueGroupCode(startDate: String, endDate: String): String = transaction {
    var code: String
    var exists: Boolean
    var attempt = 0
    val baseInput = "$startDate-$endDate"
    val digest = MessageDigest.getInstance("SHA-256")

    do {
        val inputToHash = if (attempt == 0) baseInput else "$baseInput-$attempt"
        val hashBytes = digest.digest(inputToHash.toByteArray(Charsets.UTF_8))

        // Use first 4 bytes of the hash to generate a number
        val buffer = ByteBuffer.wrap(hashBytes.take(4).toByteArray())
        val numericPart = kotlin.math.abs(buffer.int % 100000) // Ensure 0-99999

        val fiveDigitCode = numericPart.toString().padStart(5, '0')
        code = "ITN-$fiveDigitCode"

        exists = Trips.selectAll().where { Trips.groupCode eq code }.count() > 0

        if (exists) {
            attempt++
        }
    } while (exists)

    return@transaction code
}

suspend fun createCompleteTrip(
    ownerId: Int,
    destination: String,
    startDate: String,
    endDate: String,
    summary: String,
    accommodation: Accommodation,
    groupName: String,
    reservationCode: String? = null,
    extraInfo: String? = null
): Trip = transaction {
    val groupCode = generateUniqueGroupCode(startDate, endDate)

    val tripId = Trips.insert {
        it[Trips.userId] = ownerId
        it[Trips.destination] = destination
        it[Trips.startDate] = startDate
        it[Trips.endDate] = endDate
        it[Trips.summary] = summary
        it[Trips.totalMembers] = 1  // Initially just the owner
        it[Trips.accommodationName] = accommodation.name
        it[Trips.accommodationPhone] = accommodation.phone
        it[Trips.checkIn] = accommodation.checkIn
        it[Trips.checkOut] = accommodation.checkOut
        it[Trips.accommodationLatitude] = accommodation.latitude
        it[Trips.accommodationLongitude] = accommodation.longitude
        it[Trips.reservationCode] = reservationCode
        it[Trips.extraInfo] = extraInfo
        it[Trips.groupCode] = groupCode
        it[Trips.groupName] = groupName
        it[Trips.ownerId] = ownerId
    } get Trips.id

    // Add the owner as an accepted member
    TripMembers.insert {
        it[TripMembers.userId] = ownerId
        it[TripMembers.tripId] = tripId
        it[isAccepted] = true
        it[isPending] = false
    }

    return@transaction Trip(
        id = tripId,
        destination = destination,
        startDate = startDate,
        endDate = endDate,
        summary = summary,
        totalMembers = 1,
        accommodation = Accommodation(
            name = accommodation.name,
            phone = accommodation.phone,
            checkIn = accommodation.checkIn,
            checkOut = accommodation.checkOut,
            latitude = accommodation.latitude,
            longitude = accommodation.longitude,
            reservationCode = reservationCode,
            extraInfo = extraInfo
        ),
        groupCode = groupCode,
        groupName = groupName,
        ownerId = ownerId
    )
}.also { trip ->
    // Create corresponding chat group after the transaction completes
    try {
        createChatGroup(trip.groupCode, trip.groupName, ownerId)
    } catch (e: Exception) {
        println("Warning: Failed to create chat group for trip ${trip.id}: ${e.message}")
    }
}

fun createTrip(trip: Trip, userId: Int): Trip = transaction {
    val tripId = Trips.insert {
        it[Trips.userId] = userId
        it[destination] = trip.destination
        it[startDate] = trip.startDate
        it[endDate] = trip.endDate
        it[summary] = trip.summary
        it[totalMembers] = trip.totalMembers
        it[accommodationName] = trip.accommodation.name
        it[accommodationPhone] = trip.accommodation.phone
        it[checkIn] = trip.accommodation.checkIn
        it[checkOut] = trip.accommodation.checkOut
        it[accommodationLatitude] = trip.accommodation.latitude
        it[accommodationLongitude] = trip.accommodation.longitude
        it[reservationCode] = trip.accommodation.reservationCode
        it[extraInfo] = trip.accommodation.extraInfo
        it[groupCode] = trip.groupCode
        it[groupName] = trip.groupName
        it[ownerId] = trip.ownerId
    } get Trips.id

    trip.copy(id = tripId)
}

fun findTripForUser(userId: Int): Trip? = transaction {
    Trips.selectAll().where { Trips.userId eq userId }.limit(1).map {
        Trip(
            id = it[Trips.id],
            destination = it[Trips.destination],
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
            groupName = it[Trips.groupName],
            ownerId = it[Trips.ownerId]
        )
    }.singleOrNull()
}

/**
 * @deprecated This function only returns trips where the user is listed as the primary owner (userId field),
 * not trips where they are a member. Use findMemberTrips from TripMembersTable.kt instead.
 */
@Deprecated(
    "Use findMemberTrips from TripMembersTable.kt instead",
    ReplaceWith("findMemberTrips(userId)", "com.serranoie.server.repository.findMemberTrips")
)
fun findAllTripsForUser(userId: Int): List<Trip> = transaction {
    Trips.selectAll().where { Trips.userId eq userId }.map {
        Trip(
            id = it[Trips.id],
            destination = it[Trips.destination],
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
            groupName = it[Trips.groupName],
            ownerId = it[Trips.ownerId]
        )
    }
}
