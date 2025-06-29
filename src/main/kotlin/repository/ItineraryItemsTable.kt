package com.serranoie.server.repository

import com.serranoie.server.models.ItineraryItem
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

object ItineraryItems : Table() {
    val id = integer("id").autoIncrement()
    val groupCode = varchar("group_code", 20).references(Trips.groupCode)
    val name = varchar("name", 200)
    val description = text("description")
    val date = varchar("date", 20) // "30 June 2025" format
    val time = varchar("time", 10) // "03:55 PM" format
    val location = varchar("location", 300)
    val isCompleted = bool("is_completed").default(false)

    override val primaryKey = PrimaryKey(id)
}

fun createItineraryItem(
    groupCode: String,
    name: String,
    description: String,
    date: String,
    time: String,
    location: String
): ItineraryItem = transaction {
    val itemId = ItineraryItems.insert {
        it[ItineraryItems.groupCode] = groupCode
        it[ItineraryItems.name] = name
        it[ItineraryItems.description] = description
        it[ItineraryItems.date] = date
        it[ItineraryItems.time] = time
        it[ItineraryItems.location] = location
        it[isCompleted] = false
    } get ItineraryItems.id

    ItineraryItem(
        id = itemId,
        groupCode = groupCode,
        name = name,
        description = description,
        date = date,
        time = time,
        location = location,
        isCompleted = false
    )
}

fun getItineraryItemsByGroupCode(groupCode: String): List<ItineraryItem> = transaction {
    ItineraryItems.selectAll().where { ItineraryItems.groupCode eq groupCode }
        .orderBy(ItineraryItems.date to SortOrder.ASC, ItineraryItems.time to SortOrder.ASC)
        .map { row ->
            ItineraryItem(
                id = row[ItineraryItems.id],
                groupCode = row[ItineraryItems.groupCode],
                name = row[ItineraryItems.name],
                description = row[ItineraryItems.description],
                date = row[ItineraryItems.date],
                time = row[ItineraryItems.time],
                location = row[ItineraryItems.location],
                isCompleted = row[ItineraryItems.isCompleted]
            )
        }
}

fun getItineraryItemById(itemId: Int): ItineraryItem? = transaction {
    ItineraryItems.selectAll().where { ItineraryItems.id eq itemId }
        .map { row ->
            ItineraryItem(
                id = row[ItineraryItems.id],
                groupCode = row[ItineraryItems.groupCode],
                name = row[ItineraryItems.name],
                description = row[ItineraryItems.description],
                date = row[ItineraryItems.date],
                time = row[ItineraryItems.time],
                location = row[ItineraryItems.location],
                isCompleted = row[ItineraryItems.isCompleted]
            )
        }.singleOrNull()
}

fun updateItineraryItem(
    itemId: Int,
    name: String?,
    description: String?,
    date: String?,
    time: String?,
    location: String?,
    isCompleted: Boolean?
): Boolean = transaction {
    val updateCount = ItineraryItems.update({ ItineraryItems.id eq itemId }) {
        name?.let { updatedName -> it[ItineraryItems.name] = updatedName }
        description?.let { updatedDescription -> it[ItineraryItems.description] = updatedDescription }
        date?.let { updatedDate -> it[ItineraryItems.date] = updatedDate }
        time?.let { updatedTime -> it[ItineraryItems.time] = updatedTime }
        location?.let { updatedLocation -> it[ItineraryItems.location] = updatedLocation }
        isCompleted?.let { completed -> it[ItineraryItems.isCompleted] = completed }
    }
    updateCount > 0
}

fun deleteItineraryItem(itemId: Int): Boolean = transaction {
    val deleteCount = ItineraryItems.deleteWhere { ItineraryItems.id eq itemId }
    deleteCount > 0
}

fun markItineraryItemCompleted(itemId: Int, completed: Boolean): Boolean = transaction {
    val updateCount = ItineraryItems.update({ ItineraryItems.id eq itemId }) {
        it[isCompleted] = completed
    }
    updateCount > 0
}
