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
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.tripSettingsRoutes() {
    authenticate {
        // Trip Info Settings Routes
        get("/trips/{id}/info") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
            val tripId = call.parameters["id"]?.toIntOrNull()

            if (email == null || tripId == null) {
                call.respondText("Invalid request", status = HttpStatusCode.BadRequest)
                return@get
            }

            val user = findUserByEmail(email)
            if (user == null) {
                call.respondText("User not found", status = HttpStatusCode.NotFound)
                return@get
            }

            // Check if user is member or owner
            val isOwner = isUserTripOwner(user.id, tripId)
            val isMember = isTripMember(user.id, tripId)

            if (!isOwner && !isMember) {
                call.respondText("Unauthorized", status = HttpStatusCode.Forbidden)
                return@get
            }

            val tripInfo = getTripInfoSettings(tripId)
            if (tripInfo == null) {
                call.respondText("Trip not found", status = HttpStatusCode.NotFound)
                return@get
            }

            call.respond(tripInfo)
        }

        put("/trips/{id}/info") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
            val tripId = call.parameters["id"]?.toIntOrNull()

            if (email == null || tripId == null) {
                call.respondText("Invalid request", status = HttpStatusCode.BadRequest)
                return@put
            }

            val user = findUserByEmail(email)
            if (user == null) {
                call.respondText("User not found", status = HttpStatusCode.NotFound)
                return@put
            }

            // Only owner can update trip info
            if (!isUserTripOwner(user.id, tripId)) {
                call.respondText("Only the trip owner can update trip info", status = HttpStatusCode.Forbidden)
                return@put
            }

            val request = call.receive<UpdateTripInfoRequest>()
            if (updateTripInfo(tripId, request)) {
                call.respondText("Trip info updated successfully", status = HttpStatusCode.OK)
            } else {
                call.respondText("Failed to update trip info", status = HttpStatusCode.InternalServerError)
            }
        }

        // Group Settings Routes
        get("/trips/{id}/group") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
            val tripId = call.parameters["id"]?.toIntOrNull()

            if (email == null || tripId == null) {
                call.respondText("Invalid request", status = HttpStatusCode.BadRequest)
                return@get
            }

            val user = findUserByEmail(email)
            if (user == null) {
                call.respondText("User not found", status = HttpStatusCode.NotFound)
                return@get
            }

            // Check if user is member or owner
            val isOwner = isUserTripOwner(user.id, tripId)
            val isMember = isTripMember(user.id, tripId)

            if (!isOwner && !isMember) {
                call.respondText("Unauthorized", status = HttpStatusCode.Forbidden)
                return@get
            }

            val groupSettings = getGroupSettings(tripId, user.id)
            if (groupSettings == null) {
                call.respondText("Trip not found", status = HttpStatusCode.NotFound)
                return@get
            }

            call.respond(groupSettings)
        }

        put("/trips/{id}/group") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
            val tripId = call.parameters["id"]?.toIntOrNull()

            if (email == null || tripId == null) {
                call.respondText("Invalid request", status = HttpStatusCode.BadRequest)
                return@put
            }

            val user = findUserByEmail(email)
            if (user == null) {
                call.respondText("User not found", status = HttpStatusCode.NotFound)
                return@put
            }

            // Only owner can update trip settings
            if (!isUserTripOwner(user.id, tripId)) {
                call.respondText("Only the trip owner can update group settings", status = HttpStatusCode.Forbidden)
                return@put
            }

            val request = call.receive<UpdateGroupSettingsRequest>()
            if (updateGroupSettings(tripId, request)) {
                call.respondText("Group settings updated successfully", status = HttpStatusCode.OK)
            } else {
                call.respondText("Failed to update group settings", status = HttpStatusCode.InternalServerError)
            }
        }

        // Get pending members
        get("/trips/{id}/pending") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
            val tripId = call.parameters["id"]?.toIntOrNull()

            if (email == null || tripId == null) {
                call.respondText("Invalid request", status = HttpStatusCode.BadRequest)
                return@get
            }

            val user = findUserByEmail(email)
            if (user == null) {
                call.respondText("User not found", status = HttpStatusCode.NotFound)
                return@get
            }

            // Only owner can see pending members
            if (!isUserTripOwner(user.id, tripId)) {
                call.respondText("Only the trip owner can view pending members", status = HttpStatusCode.Forbidden)
                return@get
            }

            val pendingMembers = getPendingMembers(tripId)
            call.respond(pendingMembers)
        }

        // Invite member by email
        post("/trips/{id}/invite") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
            val tripId = call.parameters["id"]?.toIntOrNull()

            if (email == null || tripId == null) {
                call.respondText("Invalid request", status = HttpStatusCode.BadRequest)
                return@post
            }

            val user = findUserByEmail(email)
            if (user == null) {
                call.respondText("User not found", status = HttpStatusCode.NotFound)
                return@post
            }

            // Only owner can invite members
            if (!isUserTripOwner(user.id, tripId)) {
                call.respondText("Only the trip owner can invite members", status = HttpStatusCode.Forbidden)
                return@post
            }

            val request = call.receive<InviteMemberRequest>()

            if (inviteMemberByEmail(request.email, tripId)) {
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
        post("/trips/{id}/members/{memberId}/accept") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
            val tripId = call.parameters["id"]?.toIntOrNull()
            val memberId = call.parameters["memberId"]?.toIntOrNull()

            if (email == null || tripId == null || memberId == null) {
                call.respondText("Invalid request", status = HttpStatusCode.BadRequest)
                return@post
            }

            val user = findUserByEmail(email)
            if (user == null) {
                call.respondText("User not found", status = HttpStatusCode.NotFound)
                return@post
            }

            // Only owner can accept members
            if (!isUserTripOwner(user.id, tripId)) {
                call.respondText("Only the trip owner can manage members", status = HttpStatusCode.Forbidden)
                return@post
            }

            if (acceptTripMember(memberId, tripId)) {
                call.respond(MemberActionResponse(true, "Member accepted successfully"))
            } else {
                call.respond(MemberActionResponse(false, "Failed to accept member"))
            }
        }

        delete("/trips/{id}/members/{memberId}") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
            val tripId = call.parameters["id"]?.toIntOrNull()
            val memberId = call.parameters["memberId"]?.toIntOrNull()

            if (email == null || tripId == null || memberId == null) {
                call.respondText("Invalid request", status = HttpStatusCode.BadRequest)
                return@delete
            }

            val user = findUserByEmail(email)
            if (user == null) {
                call.respondText("User not found", status = HttpStatusCode.NotFound)
                return@delete
            }

            // Only owner can reject/remove members
            if (!isUserTripOwner(user.id, tripId)) {
                call.respondText("Only the trip owner can manage members", status = HttpStatusCode.Forbidden)
                return@delete
            }

            if (rejectTripMember(memberId, tripId)) {
                call.respond(MemberActionResponse(true, "Member removed successfully"))
            } else {
                call.respond(MemberActionResponse(false, "Failed to remove member"))
            }
        }

        // Leave Trip (for members)
        delete("/trips/{id}/leave") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
            val tripId = call.parameters["id"]?.toIntOrNull()

            if (email == null || tripId == null) {
                call.respondText("Invalid request", status = HttpStatusCode.BadRequest)
                return@delete
            }

            val user = findUserByEmail(email)
            if (user == null) {
                call.respondText("User not found", status = HttpStatusCode.NotFound)
                return@delete
            }

            // Owner cannot leave, only delete
            if (isUserTripOwner(user.id, tripId)) {
                call.respondText(
                    "Trip owner cannot leave. Use delete trip instead.",
                    status = HttpStatusCode.BadRequest
                )
                return@delete
            }

            if (leaveTrip(user.id, tripId)) {
                call.respond(MemberActionResponse(true, "Left trip successfully"))
            } else {
                call.respond(MemberActionResponse(false, "Failed to leave trip"))
            }
        }

        // Delete Trip (owner only)
        delete("/trips/{id}") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
            val tripId = call.parameters["id"]?.toIntOrNull()

            if (email == null || tripId == null) {
                call.respondText("Invalid request", status = HttpStatusCode.BadRequest)
                return@delete
            }

            val user = findUserByEmail(email)
            if (user == null) {
                call.respondText("User not found", status = HttpStatusCode.NotFound)
                return@delete
            }

            // Only owner can delete trip
            if (!isUserTripOwner(user.id, tripId)) {
                call.respondText("Only the trip owner can delete the trip", status = HttpStatusCode.Forbidden)
                return@delete
            }

            deleteTrip(tripId)
            call.respond(MemberActionResponse(true, "Trip deleted successfully"))
        }
    }
}