package com.serranoie.server.repository

import com.serranoie.server.models.Accommodation
import com.serranoie.server.models.Location
import com.serranoie.server.models.TravelDirection
import com.serranoie.server.models.Trip
import org.jetbrains.exposed.sql.Table
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
    val endDate = varchar("end_date", 10)     // ISO 8601 date format: yyyy-MM-dd
    val totalDays = integer("total_days")
    val summary = text("summary")
    val totalMembers = integer("total_members")
    val travelDirection = varchar("travel_direction", 10) // OUTBOUND / RETURN
    val hasPendingActions = bool("has_pending_actions")
    val accommodationName = varchar("accommodation_name", 100)
    val accommodationPhone = varchar("accommodation_phone", 20)
    val checkIn = varchar("check_in", 19)    // ISO 8601 date format with time
    val checkOut = varchar("check_out", 19)  // ISO 8601 date format with time
    val locationName = varchar("location_name", 100)
    val latitude = double("latitude")
    val longitude = double("longitude")
    val reservationCode = varchar("reservation_code", 50).nullable()
    val extraInfo = text("extra_info").nullable()
    val additionalInfo = text("additional_info").nullable()
    val groupCode = varchar("group_code", 20).uniqueIndex()
    val ownerId = integer("owner_id").references(Users.id)

    override val primaryKey = PrimaryKey(id)
}

// Generate a unique group code in format ITN-XXXXX
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

fun createCompleteTrip(
    ownerId: Int,
    destination: String,
    startDate: String,
    endDate: String,
    totalDays: Int,
    summary: String,
    accommodation: Accommodation,
    reservationCode: String? = null,
    extraInfo: String? = null,
    additionalInfo: String? = null
): Trip = transaction {
    val groupCode = generateUniqueGroupCode(startDate, endDate)

    val tripId = Trips.insert {
        it[Trips.userId] = ownerId
        it[Trips.destination] = destination
        it[Trips.startDate] = startDate
        it[Trips.endDate] = endDate
        it[Trips.totalDays] = totalDays
        it[Trips.summary] = summary
        it[Trips.totalMembers] = 1  // Initially just the owner
        it[Trips.travelDirection] = TravelDirection.OUTBOUND.name
        it[Trips.hasPendingActions] = false
        it[Trips.accommodationName] = accommodation.name
        it[Trips.accommodationPhone] = accommodation.phone
        it[Trips.checkIn] = accommodation.checkIn
        it[Trips.checkOut] = accommodation.checkOut
        it[Trips.locationName] = accommodation.location.name
        it[Trips.latitude] = accommodation.location.latitude
        it[Trips.longitude] = accommodation.location.longitude
        it[Trips.reservationCode] = reservationCode
        it[Trips.extraInfo] = extraInfo
        it[Trips.additionalInfo] = additionalInfo
        it[Trips.groupCode] = groupCode
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
        totalDays = totalDays,
        summary = summary,
        totalMembers = 1,
        travelDirection = TravelDirection.OUTBOUND,
        hasPendingActions = false,
        accommodation = accommodation,
        reservationCode = reservationCode,
        extraInfo = extraInfo,
        additionalInfo = additionalInfo,
        groupCode = groupCode,
        ownerId = ownerId
    )
}

fun createTrip(trip: Trip, userId: Int): Trip = transaction {
    val tripId = Trips.insert {
        it[Trips.userId] = userId
        it[destination] = trip.destination
        it[startDate] = trip.startDate
        it[endDate] = trip.endDate
        it[totalDays] = trip.totalDays
        it[summary] = trip.summary
        it[totalMembers] = trip.totalMembers
        it[travelDirection] = trip.travelDirection.name
        it[hasPendingActions] = trip.hasPendingActions
        it[accommodationName] = trip.accommodation.name
        it[accommodationPhone] = trip.accommodation.phone
        it[checkIn] = trip.accommodation.checkIn
        it[checkOut] = trip.accommodation.checkOut
        it[locationName] = trip.accommodation.location.name
        it[latitude] = trip.accommodation.location.latitude
        it[longitude] = trip.accommodation.location.longitude
        it[reservationCode] = trip.reservationCode
        it[extraInfo] = trip.extraInfo
        it[additionalInfo] = trip.additionalInfo
        it[groupCode] = trip.groupCode
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
                totalDays = it[Trips.totalDays],
                summary = it[Trips.summary],
                totalMembers = it[Trips.totalMembers],
                travelDirection = TravelDirection.valueOf(it[Trips.travelDirection]),
                hasPendingActions = it[Trips.hasPendingActions],
                accommodation = Accommodation(
                    name = it[Trips.accommodationName],
                    phone = it[Trips.accommodationPhone],
                    checkIn = it[Trips.checkIn],
                    checkOut = it[Trips.checkOut],
                    location = Location(
                        name = it[Trips.locationName], latitude = it[Trips.latitude], longitude = it[Trips.longitude]
                    )
                ),
                reservationCode = it[Trips.reservationCode],
                extraInfo = it[Trips.extraInfo],
                additionalInfo = it[Trips.additionalInfo],
                groupCode = it[Trips.groupCode],
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
                totalDays = it[Trips.totalDays],
                summary = it[Trips.summary],
                totalMembers = it[Trips.totalMembers],
                travelDirection = TravelDirection.valueOf(it[Trips.travelDirection]),
                hasPendingActions = it[Trips.hasPendingActions],
                accommodation = Accommodation(
                    name = it[Trips.accommodationName],
                    phone = it[Trips.accommodationPhone],
                    checkIn = it[Trips.checkIn],
                    checkOut = it[Trips.checkOut],
                    location = Location(
                        name = it[Trips.locationName], latitude = it[Trips.latitude], longitude = it[Trips.longitude]
                    )
                ),
                reservationCode = it[Trips.reservationCode],
                extraInfo = it[Trips.extraInfo],
                additionalInfo = it[Trips.additionalInfo],
                groupCode = it[Trips.groupCode],
                ownerId = it[Trips.ownerId]
            )
        }
}