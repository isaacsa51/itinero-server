package com.serranoie.server.routes

import com.serranoie.server.models.*
import com.serranoie.server.repository.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.chatRoutes() {
    authenticate {
        route("/chat") {
            get("/groups") {
                handleGetUserGroups(call)
            }

            get("/groups/{groupCode}/messages") {
                handleGetGroupMessages(call)
            }

            get("/groups/{groupCode}/members") {
                handleGetGroupMembers(call)
            }

            put("/messages/{messageId}") {
                handleEditMessage(call)
            }

            delete("/messages/{messageId}") {
                handleDeleteMessage(call)
            }
        }
    }
}

private suspend fun handleGetUserGroups(call: ApplicationCall) {
    try {
        val principal = call.principal<JWTPrincipal>()
        val email = principal?.payload?.getClaim("email")?.asString()

        if (email == null) {
            call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
            return
        }

        val user = findUserByEmail(email)
        if (user == null) {
            call.respondText("User not found", status = HttpStatusCode.NotFound)
            return
        }

        // Get user's trips and create chat groups for them
        val userTrips = findMemberTrips(user.id)
        val chatGroups = userTrips.map { trip ->
            ChatGroup(
                groupCode = trip.groupCode,
                groupName = trip.groupName,
                ownerId = trip.ownerId,
                createdAt = trip.startDate,
                members = emptyList(),
                lastMessage = getLastMessage(trip.groupCode)
            )
        }

        call.respond(chatGroups)

    } catch (e: Exception) {
        handleServerError(call, e)
    }
}

private suspend fun handleGetGroupMessages(call: ApplicationCall) {
    try {
        val groupCode = call.parameters["groupCode"]
        if (groupCode == null) {
            call.respondText("Group code is required", status = HttpStatusCode.BadRequest)
            return
        }

        val principal = call.principal<JWTPrincipal>()
        val email = principal?.payload?.getClaim("email")?.asString()

        if (email == null) {
            call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
            return
        }

        val user = findUserByEmail(email)
        if (user == null) {
            call.respondText("User not found", status = HttpStatusCode.NotFound)
            return
        }

        // Get trip ID from group code and check membership
        val tripId = findTripByGroupCode(groupCode)
        if (tripId == null) {
            call.respondText("Trip not found", status = HttpStatusCode.NotFound)
            return
        }

        if (!isTripMember(user.id, tripId) && !isUserTripOwner(user.id, tripId)) {
            call.respondText("Access denied to group chat", status = HttpStatusCode.Forbidden)
            return
        }

        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

        val messages = getGroupMessages(groupCode, limit, offset)
        call.respond(messages)

    } catch (e: Exception) {
        handleServerError(call, e)
    }
}

private suspend fun handleGetGroupMembers(call: ApplicationCall) {
    try {
        val groupCode = call.parameters["groupCode"]
        if (groupCode == null) {
            call.respondText("Group code is required", status = HttpStatusCode.BadRequest)
            return
        }

        val principal = call.principal<JWTPrincipal>()
        val email = principal?.payload?.getClaim("email")?.asString()

        if (email == null) {
            call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
            return
        }

        val user = findUserByEmail(email)
        if (user == null) {
            call.respondText("User not found", status = HttpStatusCode.NotFound)
            return
        }

        // Get trip ID from group code and check membership
        val tripId = findTripByGroupCode(groupCode)
        if (tripId == null) {
            call.respondText("Trip not found", status = HttpStatusCode.NotFound)
            return
        }

        if (!isTripMember(user.id, tripId) && !isUserTripOwner(user.id, tripId)) {
            call.respondText("Access denied to group", status = HttpStatusCode.Forbidden)
            return
        }

        // Get trip members
        val tripMembers = getTripMembers(tripId)
        val chatMembers = tripMembers.map { member ->
            ChatMember(
                userId = member.id,
                userName = member.name,
                joinedAt = "", // Trip members don't have joinedAt, could be enhanced later
                lastSeenMessageId = null,
                isOnline = false
            )
        }

        call.respond(chatMembers)

    } catch (e: Exception) {
        handleServerError(call, e)
    }
}

private suspend fun handleEditMessage(call: ApplicationCall) {
    try {
        val messageId = call.parameters["messageId"]?.toIntOrNull()
        if (messageId == null) {
            call.respondText("Valid message ID is required", status = HttpStatusCode.BadRequest)
            return
        }

        val principal = call.principal<JWTPrincipal>()
        val email = principal?.payload?.getClaim("email")?.asString()

        if (email == null) {
            call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
            return
        }

        val user = findUserByEmail(email)
        if (user == null) {
            call.respondText("User not found", status = HttpStatusCode.NotFound)
            return
        }

        val editRequest = call.receive<EditMessageRequest>()
        val success = editMessage(messageId, editRequest.newMessage, user.id)

        if (success) {
            call.respondText("Message updated successfully", status = HttpStatusCode.OK)
        } else {
            call.respondText(
                "Message not found or you don't have permission to edit it",
                status = HttpStatusCode.NotFound
            )
        }

    } catch (e: Exception) {
        handleServerError(call, e)
    }
}

private suspend fun handleDeleteMessage(call: ApplicationCall) {
    try {
        val messageId = call.parameters["messageId"]?.toIntOrNull()
        if (messageId == null) {
            call.respondText("Valid message ID is required", status = HttpStatusCode.BadRequest)
            return
        }

        val principal = call.principal<JWTPrincipal>()
        val email = principal?.payload?.getClaim("email")?.asString()

        if (email == null) {
            call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
            return
        }

        val user = findUserByEmail(email)
        if (user == null) {
            call.respondText("User not found", status = HttpStatusCode.NotFound)
            return
        }

        val success = deleteMessage(messageId, user.id)

        if (success) {
            call.respondText("Message deleted successfully", status = HttpStatusCode.OK)
        } else {
            call.respondText(
                "Message not found or you don't have permission to delete it",
                status = HttpStatusCode.NotFound
            )
        }

    } catch (e: Exception) {
        handleServerError(call, e)
    }
}

private suspend fun handleServerError(call: ApplicationCall, e: Exception) {
    e.printStackTrace()
    call.respondText("Internal server error", status = HttpStatusCode.InternalServerError)
}