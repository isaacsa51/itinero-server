package com.serranoie.server.repository

import com.serranoie.server.models.User
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