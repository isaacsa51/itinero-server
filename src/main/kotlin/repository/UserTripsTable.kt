package com.serranoie.server.repository

import org.jetbrains.exposed.sql.*

object UserTrips : Table("user_trips") {
    val userId = integer("user_id").references(Users.id)
    val tripId = integer("trip_id").references(Trips.id)

    override val primaryKey = PrimaryKey(userId, tripId)
}
