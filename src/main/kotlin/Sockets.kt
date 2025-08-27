package com.serranoie.server

import com.serranoie.server.chat.ChatConnectionManager
import com.serranoie.server.models.*
import com.serranoie.server.repository.*
import com.serranoie.server.security.JwtConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    val chatManager = ChatConnectionManager()

    routing {
        authenticate {
            webSocket("/chat/{groupCode}") {
                handleChatConnection(this, chatManager)
            }
        }
    }
}

private suspend fun handleChatConnection(
    session: WebSocketServerSession,
    chatManager: ChatConnectionManager
) {
    val groupCode = session.call.parameters["groupCode"] ?: run {
        session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Group code is required"))
        return
    }

    // Get JWT principal
    val principal = session.call.principal<JWTPrincipal>()
    val userEmail = principal?.payload?.getClaim("email")?.asString()

    if (userEmail == null) {
        session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Authentication required"))
        return
    }

    // Get user details
    val user = findUserByEmail(userEmail)
    if (user == null) {
        session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "User not found"))
        return
    }

    // Check if user is member of the trip (which controls chat access)
    val tripId = findTripByGroupCode(groupCode)
    if (tripId == null) {
        session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Trip not found"))
        return
    }

    if (!isTripMember(user.id, tripId) && !isUserTripOwner(user.id, tripId)) {
        session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Access denied to group"))
        return
    }

    // Ensure chat group exists - create if needed
    try {
        val trip = getTripById(tripId)
        if (trip != null) {
            // Try to create chat group - will fail gracefully if already exists
            try {
                createChatGroup(groupCode, trip.groupName, trip.ownerId)
            } catch (e: IllegalArgumentException) {
                // Chat group already exists, which is fine
            }
        }
    } catch (e: Exception) {
        println("Error ensuring chat group exists for $groupCode: ${e.message}")
    }

    // Add connection to manager
    val connection = chatManager.addConnection(session, user.id, user.name, groupCode)

    try {
        // Send recent messages to newly connected user
        val recentMessages = getGroupMessages(groupCode, limit = 50)
        for (message in recentMessages) {
            val notification = ChatNotification(
                type = MessageAction.MESSAGE_RECEIVED,
                groupCode = groupCode,
                message = message
            )
            session.send(Frame.Text(Json.encodeToString(notification)))
        }

        // Listen for incoming messages
        for (frame in session.incoming) {
            when (frame) {
                is Frame.Text -> {
                    try {
                        val messageText = frame.readText()
                        val webSocketMessage = Json.decodeFromString<WebSocketMessage>(messageText)

                        handleWebSocketMessage(webSocketMessage, user, chatManager, connection)

                    } catch (e: Exception) {
                        println("Error processing message from user ${user.id}: ${e.message}")

                        val errorNotification = ChatNotification(
                            type = MessageAction.ERROR,
                            groupCode = groupCode,
                            error = "Failed to process message: ${e.message}"
                        )
                        session.send(Frame.Text(Json.encodeToString(errorNotification)))
                    }
                }

                is Frame.Close -> {
                    println("WebSocket closed for user ${user.id} in group $groupCode")
                    break
                }

                else -> {
                    // Ignore other frame types
                }
            }
        }

    } catch (e: ClosedReceiveChannelException) {
        println("WebSocket channel closed for user ${user.id} in group $groupCode")
    } catch (e: Exception) {
        println("WebSocket error for user ${user.id} in group $groupCode: ${e.message}")
    } finally {
        // Clean up connection
        chatManager.removeConnection(connection)
    }
}

private suspend fun handleWebSocketMessage(
    webSocketMessage: WebSocketMessage,
    user: User,
    chatManager: ChatConnectionManager,
    connection: ChatConnectionManager.ChatConnection
) {
    val groupCode = webSocketMessage.groupCode

    when (webSocketMessage.type) {
        MessageAction.SEND_MESSAGE -> {
            try {
                val sendMessageRequest = Json.decodeFromString<SendMessageRequest>(webSocketMessage.data)

                if (sendMessageRequest.replyToMessageId != null) {
                    println("Replying message detected: user=${user.id} (${user.name}) group=$groupCode replyTo=${sendMessageRequest.replyToMessageId}")
                }

                val chatMessage = ChatMessage(
                    id = 0, // Will be assigned by database
                    groupCode = groupCode,
                    senderId = user.id,
                    senderName = "${user.name}${if (user.surname.isNullOrBlank()) "" else " ${user.surname}"}",
                    message = sendMessageRequest.message,
                    messageType = sendMessageRequest.messageType,
                    timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    isEdited = false,
                    replyToMessageId = sendMessageRequest.replyToMessageId
                )

                // Save message to database
                val messageId = saveMessage(chatMessage)
                val savedMessage = chatMessage.copy(id = messageId)

                if (savedMessage.replyToMessageId != null) {
                    println("REPLY - Reply message saved: newMessageId=${savedMessage.id} replyTo=${savedMessage.replyToMessageId}")
                }

                // Broadcast message to all group members
                chatManager.broadcastMessage(groupCode, savedMessage)

                if (savedMessage.replyToMessageId != null) {
                    println("Reply message broadcasted: newMessageId=${savedMessage.id} replyTo=${savedMessage.replyToMessageId}")
                }
            } catch (e: Exception) {
                println("Error sending message from user ${user.id}: ${e.message}")
            }
        }

        MessageAction.EDIT_MESSAGE -> {
            try {
                val editRequest = Json.decodeFromString<EditMessageRequest>(webSocketMessage.data)
                val messageId = editRequest.messageId

                if (messageId == null) {
                    val errorNotification = ChatNotification(
                        type = MessageAction.ERROR,
                        groupCode = groupCode,
                        error = "Missing messageId for edit"
                    )
                    chatManager.broadcastToGroup(groupCode, errorNotification, excludeUserId = user.id)
                    return
                }

                val success = editMessage(messageId, editRequest.newMessage, user.id)
                if (success) {
                    chatManager.broadcastToGroup(
                        groupCode,
                        ChatNotification(
                            type = MessageAction.EDIT_MESSAGE,
                            groupCode = groupCode,
                            userId = user.id,
                            userName = "${user.name}${if (user.surname.isNullOrBlank()) "" else " ${user.surname}"}",
                            editedMessageId = messageId,
                            editedMessage = editRequest.newMessage
                        ),
                        excludeUserId = null
                    )
                } else {
                    val errorNotification = ChatNotification(
                        type = MessageAction.ERROR,
                        groupCode = groupCode,
                        error = "Edit failed or not authorized"
                    )
                    chatManager.broadcastToGroup(groupCode, errorNotification, excludeUserId = user.id)
                }

            } catch (e: Exception) {
                println("Error editing message from user ${user.id}: ${e.message}")
            }
        }

        MessageAction.DELETE_MESSAGE -> {
            try {
                val deleteRequest = Json.decodeFromString<DeleteMessageRequest>(webSocketMessage.data)
                val success = deleteMessage(deleteRequest.messageId, user.id)

                if (success) {
                    chatManager.broadcastToGroup(
                        groupCode,
                        ChatNotification(
                            type = MessageAction.DELETE_MESSAGE,
                            groupCode = groupCode,
                            userId = user.id,
                            userName = "${user.name}${if (user.surname.isNullOrBlank()) "" else " ${user.surname}"}",
                            deletedMessageId = deleteRequest.messageId
                        ),
                        excludeUserId = null
                    )
                } else {
                    val errorNotification = ChatNotification(
                        type = MessageAction.ERROR,
                        groupCode = groupCode,
                        error = "Delete failed or not authorized"
                    )
                    chatManager.broadcastToGroup(groupCode, errorNotification, excludeUserId = user.id)
                }
            } catch (e: Exception) {
                println("Error deleting message from user ${user.id}: ${e.message}")
            }
        }

        MessageAction.TYPING_START, MessageAction.TYPING_STOP -> {
            try {
                val typingIndicator = TypingIndicator(
                    userId = user.id,
                    userName = "${user.name}${if (user.surname.isNullOrBlank()) "" else " ${user.surname}"}",
                    isTyping = webSocketMessage.type == MessageAction.TYPING_START
                )

                chatManager.broadcastTypingIndicator(groupCode, typingIndicator, excludeUserId = user.id)

            } catch (e: Exception) {
                println("Error handling typing indicator from user ${user.id}: ${e.message}")
                e.printStackTrace()
            }
        }

        else -> {
            println("Unhandled message type: ${webSocketMessage.type} from user ${user.id}")
        }
    }
}
