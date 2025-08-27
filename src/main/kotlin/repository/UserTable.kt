package com.serranoie.server.repository

import com.serranoie.server.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction


object Users : Table() {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 50)
    val surname = varchar("surname", 50)
    val phone = varchar("phone", 15)
    val email = varchar("email", 100).uniqueIndex()
    val passwordHash = varchar("password_hash", 64)

    override val primaryKey = PrimaryKey(id)
}


fun createUser(user: User): User = transaction {
    val userId = Users.insert {
        it[name] = user.name
        it[surname] = user.surname
        it[phone] = user.phone
        it[email] = user.email
        it[passwordHash] = user.passwordHash
    } get Users.id

    user.copy(id = userId)
}

fun findUserByEmail(email: String): User? = transaction {
    Users.selectAll()
        .where { Users.email eq email }
        .map {
            User(
                id = it[Users.id],
                name = it[Users.name],
                surname = it[Users.surname],
                phone = it[Users.phone],
                email = it[Users.email],
                passwordHash = it[Users.passwordHash]
            )
        }.singleOrNull()
}

fun deleteUser(userId: Int): Boolean = transaction {
    // First, delete all related data to avoid foreign key constraint violations

    // 1. Delete expense debtors where user is involved
    ExpenseDebtors.deleteWhere { ExpenseDebtors.userId eq userId }

    // 2. Get trips owned by this user and their group codes for itinerary deletion
    val userOwnedTrips = Trips.selectAll().where { Trips.ownerId eq userId }.map {
        it[Trips.id] to it[Trips.groupCode]
    }

    // 3. For each trip owned by the user, delete all related data
    userOwnedTrips.forEach { (tripId, groupCode) ->
        // Delete all trip members for this trip
        TripMembers.deleteWhere { TripMembers.tripId eq tripId }

        // Delete all expenses and their debtors for this trip
        val tripExpenses = Expenses.selectAll().where { Expenses.tripId eq tripId }.map { it[Expenses.id] }
        tripExpenses.forEach { expenseId ->
            ExpenseDebtors.deleteWhere { ExpenseDebtors.expenseId eq expenseId }
        }
        Expenses.deleteWhere { Expenses.tripId eq tripId }

        // Delete itinerary items for this trip (using groupCode)
        ItineraryItems.deleteWhere { ItineraryItems.groupCode eq groupCode }
    }

    // 4. Delete expenses paid by this user (not covered by trip deletion)
    val userExpenses = Expenses.selectAll().where { Expenses.paidByUserId eq userId }.map { it[Expenses.id] }
    userExpenses.forEach { expenseId ->
        ExpenseDebtors.deleteWhere { ExpenseDebtors.expenseId eq expenseId }
    }
    Expenses.deleteWhere { Expenses.paidByUserId eq userId }

    // 5. Delete trip memberships where user is a member
    TripMembers.deleteWhere { TripMembers.userId eq userId }

    // 6. Delete trips owned by user
    Trips.deleteWhere { Trips.ownerId eq userId }

    // 7. Delete from legacy UserTrips table
    UserTrips.deleteWhere { UserTrips.userId eq userId }

    // 8. Finally, delete the user
    val deletedRows = Users.deleteWhere { Users.id eq userId }
    deletedRows > 0
}
