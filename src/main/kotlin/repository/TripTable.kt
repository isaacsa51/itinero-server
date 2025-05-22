package com.serranoie.server.repository

import com.serranoie.server.models.Accommodation
import com.serranoie.server.models.Location
import com.serranoie.server.models.TravelDirection
import com.serranoie.server.models.Trip
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate

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

    override val primaryKey = PrimaryKey(id)
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
    } get Trips.id

    trip.copy(id = tripId)
}

fun findTripForUser(userId: Int): Trip? = transaction {
    Trips.selectAll()
        .where { Trips.userId eq userId }
        .limit(1)
        .map {
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
                        name = it[Trips.locationName], 
                        latitude = it[Trips.latitude], 
                        longitude = it[Trips.longitude]
                    )
                )
            )
        }.singleOrNull()
}

fun findAllTripsForUser(userId: Int): List<Trip> = transaction {
    Trips.selectAll()
        .where { Trips.userId eq userId }
        .map {
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
                        name = it[Trips.locationName], 
                        latitude = it[Trips.latitude], 
                        longitude = it[Trips.longitude]
                    )
                )
            )
        }
}