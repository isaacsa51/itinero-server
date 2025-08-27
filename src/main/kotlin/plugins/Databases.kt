package com.serranoie.server.plugins

import com.serranoie.server.models.Expense
import com.serranoie.server.repository.*
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
        SchemaUtils.create(
            Users,
            Trips,
            UserTrips,
            TripMembers,
            TripInfoTable,
            ItineraryItems,
            Expenses,
            ExpenseDebtors,
            ChatGroupsTable,
            ChatMessagesTable
        )
    }
}
