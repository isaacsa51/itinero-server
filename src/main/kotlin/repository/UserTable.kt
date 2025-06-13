package com.serranoie.server.repository

import com.serranoie.server.models.*
import org.jetbrains.exposed.sql.*
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

/**
 * @deprecated This function uses the legacy UserTrips table instead of TripMembers.
 * Use findMemberTrips from TripMembersTable.kt instead, which uses the newer schema
 * with proper member status tracking.
 */
@Deprecated(
    "Use findMemberTrips from TripMembersTable.kt instead",
    ReplaceWith("findMemberTrips(userId)", "com.serranoie.server.repository.findMemberTrips")
)
fun findTripsForUser(userId: Int): List<Trip> = transaction {
    (UserTrips innerJoin Trips)
        .selectAll()
        .where { UserTrips.userId eq userId }
        .map {
            Trip(
                id = it[Trips.id],
                destination = it[Trips.destination],
                groupName = it[Trips.groupName],
                startDate = it[Trips.startDate],
                endDate = it[Trips.endDate],
                summary = it[Trips.summary],
                totalMembers = it[Trips.totalMembers],
                accommodation = Accommodation(
                    name = it[Trips.accommodationName],
                    phone = it[Trips.accommodationPhone],
                    checkIn = it[Trips.checkIn],
                    checkOut = it[Trips.checkOut],
                    location = it[Trips.location],
                    mapUri = it[Trips.mapUri]
                ),
                reservationCode = it[Trips.reservationCode],
                extraInfo = it[Trips.extraInfo],
                additionalInfo = it[Trips.additionalInfo],
                groupCode = it[Trips.groupCode],
                ownerId = it[Trips.ownerId]
            )
        }
}
