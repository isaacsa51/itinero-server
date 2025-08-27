package com.serranoie.server.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class ChatMessage(
    val id: Int,
    val groupCode: String,
    val senderId: Int,
    val senderName: String,
    val message: String,
    val messageType: MessageType,
    val timestamp: String,
    val isEdited: Boolean = false,
    @SerialName("replyToId") val replyToMessageId: Int? = null
)

@Serializable
data class ChatGroup(
    val groupCode: String,
    val groupName: String,
    val ownerId: Int,
    val createdAt: String,
    val members: List<ChatMember> = emptyList(),
    val lastMessage: ChatMessage? = null
)

@Serializable
data class ChatMember(
    val userId: Int,
    val userName: String,
    val joinedAt: String,
    val lastSeenMessageId: Int? = null,
    val isOnline: Boolean = false
)

@Serializable
data class WebSocketMessage(
    val type: MessageAction,
    val groupCode: String,
    val data: String
)

@Serializable
data class SendMessageRequest(
    val message: String,
    val messageType: MessageType = MessageType.TEXT,
    val replyToMessageId: Int? = null
)

@Serializable
data class EditMessageRequest(
    val newMessage: String,
    val messageId: Int? = null
)

@Serializable
data class DeleteMessageRequest(
    val messageId: Int
)

@Serializable
data class TypingIndicator(
    val userId: Int,
    val userName: String,
    val isTyping: Boolean
)

@Serializable
enum class MessageType {
    TEXT, IMAGE, FILE, SYSTEM
}

@Serializable
enum class MessageAction {
    JOIN_GROUP,
    LEAVE_GROUP,
    SEND_MESSAGE,
    EDIT_MESSAGE,
    DELETE_MESSAGE,
    TYPING_START,
    TYPING_STOP,
    USER_JOINED,
    USER_LEFT,
    MESSAGE_RECEIVED,
    ERROR
}

@Serializable
data class ChatNotification(
    val type: MessageAction,
    val groupCode: String,
    val userId: Int? = null,
    val userName: String? = null,
    val message: ChatMessage? = null,
    val typingIndicator: TypingIndicator? = null,
    val error: String? = null,
    val editedMessageId: Int? = null,
    val editedMessage: String? = null,
    val deletedMessageId: Int? = null
)