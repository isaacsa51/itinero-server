package com.serranoie.server.chat

import com.serranoie.server.models.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class ChatConnectionManager {
    private val connections = ConcurrentHashMap<String, MutableSet<ChatConnection>>()
    private val userConnections = ConcurrentHashMap<Int, MutableSet<ChatConnection>>()
    private val connectionCounter = AtomicInteger(0)

    data class ChatConnection(
        val id: String,
        val session: WebSocketSession,
        val userId: Int,
        val userName: String,
        val groupCode: String
    )

    suspend fun addConnection(
        session: WebSocketSession,
        userId: Int,
        userName: String,
        groupCode: String
    ): ChatConnection {
        val connectionId = "conn_${connectionCounter.incrementAndGet()}_${userId}_${System.currentTimeMillis()}"
        val connection = ChatConnection(connectionId, session, userId, userName, groupCode)

        // Add to group connections
        connections.computeIfAbsent(groupCode) { mutableSetOf() }.add(connection)

        // Add to user connections
        userConnections.computeIfAbsent(userId) { mutableSetOf() }.add(connection)

        // Notify group that user joined
        broadcastToGroup(
            groupCode, ChatNotification(
                type = MessageAction.USER_JOINED,
                groupCode = groupCode,
                userId = userId,
                userName = userName
            ), excludeUserId = null
        )

        println("User $userName ($userId) connected to group $groupCode. Total connections: ${getTotalConnections()}")

        return connection
    }

    suspend fun removeConnection(connection: ChatConnection) {
        val groupCode = connection.groupCode
        val userId = connection.userId

        // Remove from group connections
        connections[groupCode]?.remove(connection)
        if (connections[groupCode]?.isEmpty() == true) {
            connections.remove(groupCode)
        }

        // Remove from user connections
        userConnections[userId]?.remove(connection)
        if (userConnections[userId]?.isEmpty() == true) {
            userConnections.remove(userId)
        }

        // Check if user is still connected to this group through another session
        val userStillInGroup = connections[groupCode]?.any { it.userId == userId } == true

        // If user has no more connections to this group, notify others
        if (!userStillInGroup) {
            broadcastToGroup(
                groupCode, ChatNotification(
                    type = MessageAction.USER_LEFT,
                    groupCode = groupCode,
                    userId = userId,
                    userName = connection.userName
                ), excludeUserId = userId
            )
        }

        println("User ${connection.userName} ($userId) disconnected from group $groupCode. Total connections: ${getTotalConnections()}")
    }

    suspend fun broadcastToGroup(groupCode: String, notification: ChatNotification, excludeUserId: Int? = null) {
        val message = Json.encodeToString(notification)

        val groupConnections = connections[groupCode] ?: return

        val connectionsToRemove = mutableSetOf<ChatConnection>()

        for (connection in groupConnections) {
            if (excludeUserId != null && connection.userId == excludeUserId) {
                continue
            }

            try {
                connection.session.send(Frame.Text(message))
            } catch (e: Exception) {
                println("Failed to send message to user ${connection.userId}: ${e.message}")
                connectionsToRemove.add(connection)
            }
        }

        // Clean up failed connections
        connectionsToRemove.forEach { removeConnection(it) }
    }

    suspend fun sendToUser(userId: Int, notification: ChatNotification) {
        val message = Json.encodeToString(notification)
        val userSessions = userConnections[userId] ?: return

        val connectionsToRemove = mutableSetOf<ChatConnection>()

        for (connection in userSessions) {
            try {
                connection.session.send(Frame.Text(message))
            } catch (e: Exception) {
                println("Failed to send message to user $userId: ${e.message}")
                connectionsToRemove.add(connection)
            }
        }

        // Clean up failed connections
        connectionsToRemove.forEach { removeConnection(it) }
    }

    suspend fun broadcastMessage(groupCode: String, message: ChatMessage) {
        broadcastToGroup(
            groupCode, ChatNotification(
                type = MessageAction.MESSAGE_RECEIVED,
                groupCode = groupCode,
                message = message
            )
        )
    }

    suspend fun broadcastTypingIndicator(groupCode: String, typingIndicator: TypingIndicator, excludeUserId: Int) {
        broadcastToGroup(
            groupCode, ChatNotification(
                type = if (typingIndicator.isTyping) MessageAction.TYPING_START else MessageAction.TYPING_STOP,
                groupCode = groupCode,
                typingIndicator = typingIndicator
            ), excludeUserId = excludeUserId
        )
    }

    fun getGroupConnections(groupCode: String): Set<ChatConnection> {
        return connections[groupCode]?.toSet() ?: emptySet()
    }

    fun getUserConnections(userId: Int): Set<ChatConnection> {
        return userConnections[userId]?.toSet() ?: emptySet()
    }

    fun isUserOnline(userId: Int): Boolean {
        return userConnections[userId]?.isNotEmpty() == true
    }

    fun getOnlineUsersInGroup(groupCode: String): Set<Int> {
        return connections[groupCode]?.map { it.userId }?.toSet() ?: emptySet()
    }

    fun getTotalConnections(): Int {
        return connections.values.sumOf { it.size }
    }

    fun getGroupConnectionCount(groupCode: String): Int {
        return connections[groupCode]?.size ?: 0
    }
}