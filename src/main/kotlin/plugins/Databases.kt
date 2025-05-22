package com.serranoie.server.plugins

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

    transaction {
        SchemaUtils.create(Users, Trips, UserTrips)
    }
}