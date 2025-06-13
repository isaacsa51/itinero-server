package com.serranoie.server.repository

import org.jetbrains.exposed.sql.Table

object TripInfoTable : Table("trip_info_settings") {
    val tripId = integer("trip_id").references(Trips.id).uniqueIndex()
    val hotelName = varchar("hotel_name", 100).nullable()
    val checkInDate = varchar("check_in_date", 19).nullable()  // ISO date format
    val checkOutDate = varchar("check_out_date", 19).nullable() // ISO date format
    val phone = varchar("phone", 15).nullable()
    val reservationCode = varchar("reservation_code", 50).nullable()
    val location = text("location").nullable()
    val locationNotes = text("location_notes").nullable()
    val extraInfo = text("extra_info").nullable()
}