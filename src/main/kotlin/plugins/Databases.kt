package com.serranoie.server.plugins

import com.serranoie.server.repository.ItineraryItems
import com.serranoie.server.repository.TripInfoTable
import com.serranoie.server.repository.TripMembers
import com.serranoie.server.repository.Trips
import com.serranoie.server.repository.UserTrips
import com.serranoie.server.repository.Users
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun configureDatabases() {
    Database.connect(
        url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
        user = "root",
        driver = "org.h2.Driver",
        password = "",
    )

    org.h2.tools.Server.createWebServer("-web", "-webAllowOthers", "-webPort", "8082").start()

    transaction {
        SchemaUtils.create(Users, Trips, UserTrips, TripMembers, TripInfoTable, ItineraryItems)
    }
}
