package com.serranoie.server.repository

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object UserTrips : Table() {
    val userId = integer("user_id").references(Users.id)
    val tripId = integer("trip_id").references(Trips.id)

    override val primaryKey = PrimaryKey(userId, tripId)
}

fun addUserToTrip(userId: Int, tripId: Int) = transaction {
    // Check if the association already exists
    val exists = UserTrips.selectAll()
        .where { (UserTrips.userId eq userId) and (UserTrips.tripId eq tripId) }
        .count() > 0

    if (!exists) {
        UserTrips.insert {
            it[UserTrips.userId] = userId
            it[UserTrips.tripId] = tripId
        }
    }
}