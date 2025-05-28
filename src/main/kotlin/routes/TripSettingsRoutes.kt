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

private data class TripAccessResult(
    val tripId: Int,
    val user: User,
    val isOwner: Boolean,
    val isMember: Boolean
)

fun Route.tripSettingsRoutes() {
    authenticate {
        // Trip Info Settings Routes
        get("/trips/{groupCode}/info") {
            val result = validateTripAccess(call) ?: return@get

            val tripInfo = getTripInfoSettings(result.tripId)
            if (tripInfo == null) {
                call.respondText("Trip not found", status = HttpStatusCode.NotFound)
                return@get
            }

            call.respond(tripInfo)
        }

        put("/trips/{groupCode}/info") {
            val result = validateTripAccess(call, requireOwner = true) ?: return@put

            val request = call.receive<UpdateTripInfoRequest>()
            if (updateTripInfo(result.tripId, request)) {
                call.respondText("Trip info updated successfully", status = HttpStatusCode.OK)
            } else {
                call.respondText("Failed to update trip info", status = HttpStatusCode.InternalServerError)
            }
        }

        // Group Settings Routes
        get("/trips/{groupCode}/group") {
            val result = validateTripAccess(call) ?: return@get

            val groupSettings = getGroupSettings(result.tripId, result.user.id)
            if (groupSettings == null) {
                call.respondText("Trip not found", status = HttpStatusCode.NotFound)
                return@get
            }

            call.respond(groupSettings)
        }

        put("/trips/{groupCode}/group") {
            val result = validateTripAccess(call, requireOwner = true) ?: return@put

            val request = call.receive<UpdateGroupSettingsRequest>()
            if (updateGroupSettings(result.tripId, request)) {
                call.respondText("Group settings updated successfully", status = HttpStatusCode.OK)
            } else {
                call.respondText("Failed to update group settings", status = HttpStatusCode.InternalServerError)
            }
        }

        // Get pending members
        get("/trips/{groupCode}/pending") {
            val result = validateTripAccess(call, requireOwner = true) ?: return@get

            val pendingMembers = getPendingMembers(result.tripId)
            call.respond(pendingMembers)
        }

        // Invite member by email
        post("/trips/{groupCode}/invite") {
            val result = validateTripAccess(call, requireOwner = true) ?: return@post

            val request = call.receive<InviteMemberRequest>()

            if (inviteMemberByEmail(request.email, result.tripId)) {
                call.respond(MemberActionResponse(true, "Invitation sent successfully"))
            } else {
                call.respond(
                    MemberActionResponse(
                        false,
                        "Failed to send invitation. User might not exist or is already a member."
                    )
                )
            }
        }

        // Join group by code
        post("/groups/join") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()

            if (email == null) {
                call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
                return@post
            }

            val user = findUserByEmail(email)
            if (user == null) {
                call.respondText("User not found", status = HttpStatusCode.NotFound)
                return@post
            }

            val request = call.receive<JoinGroupRequest>()
            val tripId = joinGroupByCode(user.id, request.groupCode)

            if (tripId != null) {
                call.respond(MemberActionResponse(true, "Join request sent. Waiting for approval."))
            } else {
                call.respond(MemberActionResponse(false, "Invalid group code"))
            }
        }

        // Member Management
        post("/trips/{groupCode}/members/{memberId}/accept") {
            val memberId = call.parameters["memberId"]?.toIntOrNull()
            if (memberId == null) {
                call.respondText("Invalid member ID", status = HttpStatusCode.BadRequest)
                return@post
            }

            val result = validateTripAccess(call, requireOwner = true) ?: return@post

            if (acceptTripMember(memberId, result.tripId)) {
                call.respond(MemberActionResponse(true, "Member accepted successfully"))
            } else {
                call.respond(MemberActionResponse(false, "Failed to accept member"))
            }
        }

        delete("/trips/{groupCode}/members/{memberId}") {
            val memberId = call.parameters["memberId"]?.toIntOrNull()
            if (memberId == null) {
                call.respondText("Invalid member ID", status = HttpStatusCode.BadRequest)
                return@delete
            }

            val result = validateTripAccess(call, requireOwner = true) ?: return@delete

            if (rejectTripMember(memberId, result.tripId)) {
                call.respond(MemberActionResponse(true, "Member removed successfully"))
            } else {
                call.respond(MemberActionResponse(false, "Failed to remove member"))
            }
        }

        // Leave Trip (for members)
        delete("/trips/{groupCode}/leave") {
            val result = validateTripAccess(call) ?: return@delete

            // Owner cannot leave, only delete
            if (result.isOwner) {
                call.respondText(
                    "Trip owner cannot leave. Use delete trip instead.",
                    status = HttpStatusCode.BadRequest
                )
                return@delete
            }

            if (leaveTrip(result.user.id, result.tripId)) {
                call.respond(MemberActionResponse(true, "Left trip successfully"))
            } else {
                call.respond(MemberActionResponse(false, "Failed to leave trip"))
            }
        }

        // Delete Trip (owner only)
        delete("/trips/{groupCode}") {
            val result = validateTripAccess(call, requireOwner = true) ?: return@delete

            deleteTrip(result.tripId)
            call.respond(MemberActionResponse(true, "Trip deleted successfully"))
        }
    }
}

// Helper function to validate trip access
private suspend fun validateTripAccess(
    call: ApplicationCall,
    requireOwner: Boolean = false
): TripAccessResult? {
    val principal = call.principal<JWTPrincipal>()
    val email = principal?.payload?.getClaim("email")?.asString()
    val groupCode = call.parameters["groupCode"]

    if (email == null || groupCode == null) {
        call.respondText("Invalid request", status = HttpStatusCode.BadRequest)
        return null
    }

    val user = findUserByEmail(email)
    if (user == null) {
        call.respondText("User not found", status = HttpStatusCode.NotFound)
        return null
    }

    // Find trip by group code
    val tripId = findTripByGroupCode(groupCode)
    if (tripId == null) {
        call.respondText("Trip not found", status = HttpStatusCode.NotFound)
        return null
    }

    // Check if user is member or owner
    val isOwner = isUserTripOwner(user.id, tripId)
    val isMember = isTripMember(user.id, tripId)

    // If we require owner and user is not the owner
    if (requireOwner && !isOwner) {
        call.respondText("Only the trip owner can perform this action", status = HttpStatusCode.Forbidden)
        return null
    }

    // If user is neither owner nor member
    if (!isOwner && !isMember) {
        call.respondText("Unauthorized", status = HttpStatusCode.Forbidden)
        return null
    }

    return TripAccessResult(tripId, user, isOwner, isMember)
}
