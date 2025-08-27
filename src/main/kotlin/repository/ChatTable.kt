package com.serranoie.server.repository

import com.serranoie.server.models.*
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Database Tables
object ChatGroupsTable : IntIdTable() {
    val groupCode = varchar("group_code", 50).uniqueIndex()
    val groupName = varchar("group_name", 100)
    val ownerId = integer("owner_id").references(Users.id)
    val createdAt = varchar("created_at", 30) // ISO datetime format
}

object ChatMessagesTable : IntIdTable() {
    val groupCode = varchar("group_code", 50).references(ChatGroupsTable.groupCode)
    val senderId = integer("sender_id").references(Users.id)
    val senderName = varchar("sender_name", 100)
    val message = text("message")
    val messageType = enumeration("message_type", MessageType::class).default(MessageType.TEXT)
    val timestamp = varchar("timestamp", 30) // ISO datetime format
    val isEdited = bool("is_edited").default(false)
    val replyToMessageId = integer("reply_to_message_id").references(id).nullable()
}

// Repository functions for chat groups (automatically created when trips are created)
suspend fun createChatGroup(groupCode: String, groupName: String, ownerId: Int): ChatGroup = transaction {
    // Check if group already exists
    val existingGroup = ChatGroupsTable.selectAll()
        .where { ChatGroupsTable.groupCode eq groupCode }
        .singleOrNull()

    if (existingGroup != null) {
        throw IllegalArgumentException("Chat group with code $groupCode already exists")
    }

    val currentTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    ChatGroupsTable.insert {
        it[ChatGroupsTable.groupCode] = groupCode
        it[ChatGroupsTable.groupName] = groupName
        it[ChatGroupsTable.ownerId] = ownerId
        it[ChatGroupsTable.createdAt] = currentTime
    }

    ChatGroup(
        groupCode = groupCode,
        groupName = groupName,
        ownerId = ownerId,
        createdAt = currentTime
    )
}

suspend fun saveMessage(message: ChatMessage): Int = transaction {
    val insertStatement = ChatMessagesTable.insert {
        it[ChatMessagesTable.groupCode] = message.groupCode
        it[ChatMessagesTable.senderId] = message.senderId
        it[ChatMessagesTable.senderName] = message.senderName
        it[ChatMessagesTable.message] = message.message
        it[ChatMessagesTable.messageType] = message.messageType
        it[ChatMessagesTable.timestamp] = message.timestamp
        it[ChatMessagesTable.isEdited] = message.isEdited
        it[ChatMessagesTable.replyToMessageId] = message.replyToMessageId
    }

    insertStatement[ChatMessagesTable.id].value
}

suspend fun getGroupMessages(groupCode: String, limit: Int = 50, offset: Int = 0): List<ChatMessage> = transaction {
    val query = ChatMessagesTable.selectAll()
        .where { ChatMessagesTable.groupCode eq groupCode }
        .orderBy(ChatMessagesTable.timestamp, SortOrder.DESC)

    val messages = if (offset > 0) {
        query.drop(offset).take(limit)
    } else {
        query.take(limit)
    }

    messages.map { row ->
        ChatMessage(
            id = row[ChatMessagesTable.id].value,
            groupCode = row[ChatMessagesTable.groupCode],
            senderId = row[ChatMessagesTable.senderId],
            senderName = row[ChatMessagesTable.senderName],
            message = row[ChatMessagesTable.message],
            messageType = row[ChatMessagesTable.messageType],
            timestamp = row[ChatMessagesTable.timestamp],
            isEdited = row[ChatMessagesTable.isEdited],
            replyToMessageId = row[ChatMessagesTable.replyToMessageId]
        )
    }.reversed()
}

suspend fun editMessage(messageId: Int, newContent: String, userId: Int): Boolean = transaction {
    val updatedRows = ChatMessagesTable.update({
    (ChatMessagesTable.id eq messageId) and (ChatMessagesTable.senderId eq userId)
    }) {
        it[ChatMessagesTable.message] = newContent
        it[ChatMessagesTable.isEdited] = true
    }
    updatedRows > 0
}

suspend fun deleteMessage(messageId: Int, userId: Int): Boolean = transaction {
    val deletedRows = ChatMessagesTable.deleteWhere {
        (ChatMessagesTable.id eq messageId) and (ChatMessagesTable.senderId eq userId)
    }
    deletedRows > 0
}

suspend fun getLastMessage(groupCode: String): ChatMessage? = transaction {
    ChatMessagesTable.selectAll()
        .where { ChatMessagesTable.groupCode eq groupCode }
        .orderBy(ChatMessagesTable.timestamp, SortOrder.DESC)
        .limit(1)
        .singleOrNull()?.let { row ->
            ChatMessage(
                id = row[ChatMessagesTable.id].value,
                groupCode = row[ChatMessagesTable.groupCode],
                senderId = row[ChatMessagesTable.senderId],
                senderName = row[ChatMessagesTable.senderName],
                message = row[ChatMessagesTable.message],
                messageType = row[ChatMessagesTable.messageType],
                timestamp = row[ChatMessagesTable.timestamp],
                isEdited = row[ChatMessagesTable.isEdited],
                replyToMessageId = row[ChatMessagesTable.replyToMessageId]
            )
        }
}

// Following functions are removed because they are no longer needed
// suspend fun addUserToChat(groupCode: String, userId: Int, userName: String): Boolean 
// suspend fun removeUserFromChat(groupCode: String, userId: Int): Boolean 
// suspend fun getUserGroups(userId: Int): List<ChatGroup> 
// suspend fun getGroupMembers(groupCode: String): List<ChatMember> 
// suspend fun isUserInGroup(userId: Int, groupCode: String): Boolean 
// suspend fun updateLastSeenMessage(userId: Int, groupCode: String, messageId: Int): Boolean 