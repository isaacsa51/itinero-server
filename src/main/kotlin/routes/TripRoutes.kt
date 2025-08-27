package com.serranoie.server.routes

import com.serranoie.server.models.CreateTripRequest
import com.serranoie.server.repository.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

fun Route.tripAssociationRoutes() {
    authenticate {
        get("/trips") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()

            if (email == null) {
                call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
                return@get
            }

            val user = findUserByEmail(email)
            if (user == null) {
                call.respondText("User not found", status = HttpStatusCode.NotFound)
                return@get
            }

            val trips = getTripsForUser(user.id)
            call.respond(trips)
        }

        get("/trips/{groupCode}") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
            val groupCode = call.parameters["groupCode"]

            if (email == null || groupCode == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid request")
                return@get
            }

            val user = findUserByEmail(email)
            if (user == null) {
                call.respond(HttpStatusCode.NotFound, "User not found")
                return@get
            }

            val tripId = findTripByGroupCode(groupCode)
            if (tripId == null) {
                call.respond(HttpStatusCode.NotFound, "Trip not found")
                return@get
            }

            val tripDetails = getTripById(tripId)
            call.respond(tripDetails ?: HttpStatusCode.NoContent)
        }

        get("/trips/{groupCode}/members") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
            val groupCode = call.parameters["groupCode"]

            if (email == null || groupCode == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid request")
                return@get
            }

            val user = findUserByEmail(email)
            if (user == null) {
                call.respond(HttpStatusCode.NotFound, "User not found")
                return@get
            }

            val tripId = findTripByGroupCode(groupCode)
            if (tripId == null) {
                call.respond(HttpStatusCode.NotFound, "Trip not found")
                return@get
            }

            // Check if user is trip member or owner
            if (!isTripMember(user.id, tripId) && !isUserTripOwner(user.id, tripId)) {
                call.respond(HttpStatusCode.Forbidden, "Only trip members can view trip members")
                return@get
            }

            val allMembers = getTripMembers(tripId)
            call.respond(allMembers)
        }

        post("/trips/{groupCode}/members/{memberId}/accept") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
            val groupCode = call.parameters["groupCode"]
            val memberId = call.parameters["memberId"]?.toIntOrNull()

            if (email == null || groupCode == null || memberId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid request")
                return@post
            }

            val user = findUserByEmail(email)
            if (user == null) {
                call.respond(HttpStatusCode.NotFound, "User not found")
                return@post
            }

            val tripId = findTripByGroupCode(groupCode)
            if (tripId == null) {
                call.respond(HttpStatusCode.NotFound, "Trip not found")
                return@post
            }

            // Check if user is trip owner
            if (!isUserTripOwner(user.id, tripId)) {
                call.respond(HttpStatusCode.Forbidden, "Only trip owner can accept members")
                return@post
            }

            val success = acceptTripMember(memberId, tripId)
            if (success) {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Member accepted successfully"))
            } else {
                call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "Failed to accept member"))
            }
        }

        delete("/trips/{groupCode}/members/{memberId}") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
            val groupCode = call.parameters["groupCode"]
            val memberId = call.parameters["memberId"]?.toIntOrNull()

            if (email == null || groupCode == null || memberId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid request")
                return@delete
            }

            val user = findUserByEmail(email)
            if (user == null) {
                call.respond(HttpStatusCode.NotFound, "User not found")
                return@delete
            }

            val tripId = findTripByGroupCode(groupCode)
            if (tripId == null) {
                call.respond(HttpStatusCode.NotFound, "Trip not found")
                return@delete
            }

            // Check if user is trip owner
            if (!isUserTripOwner(user.id, tripId)) {
                call.respond(HttpStatusCode.Forbidden, "Only trip owner can remove members")
                return@delete
            }

            // Don't allow owner to remove themselves
            if (memberId == user.id) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Owner cannot remove themselves"))
                return@delete
            }

            val success = rejectTripMember(memberId, tripId)
            if (success) {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Member removed successfully"))
            } else {
                call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "Failed to remove member"))
            }
        }

        post("/trips/{groupCode}/members/{memberId}/make-owner") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
            val groupCode = call.parameters["groupCode"]
            val memberId = call.parameters["memberId"]?.toIntOrNull()

            if (email == null || groupCode == null || memberId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid request")
                return@post
            }

            val user = findUserByEmail(email)
            if (user == null) {
                call.respond(HttpStatusCode.NotFound, "User not found")
                return@post
            }

            val tripId = findTripByGroupCode(groupCode)
            if (tripId == null) {
                call.respond(HttpStatusCode.NotFound, "Trip not found")
                return@post
            }

            // Check if user is trip owner
            if (!isUserTripOwner(user.id, tripId)) {
                call.respond(HttpStatusCode.Forbidden, "Only trip owner can transfer ownership")
                return@post
            }

            // Check if the target member is accepted (not pending)
            if (!isTripMember(memberId, tripId)) {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Cannot make pending member an owner"))
                return@post
            }

            val success = transferTripOwnership(tripId, memberId)
            if (success) {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Ownership transferred successfully"))
            } else {
                call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "Failed to transfer ownership"))
            }
        }

        post("/trips/{groupCode}/join") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
            val groupCode = call.parameters["groupCode"]

            if (email == null || groupCode == null) {
                call.respondText("Invalid request", status = HttpStatusCode.BadRequest)
                return@post
            }

            val user = findUserByEmail(email)
            if (user == null) {
                call.respondText("User not found", status = HttpStatusCode.NotFound)
                return@post
            }

            val tripId = findTripByGroupCode(groupCode)
            if (tripId == null) {
                call.respond(HttpStatusCode.NotFound, "Trip not found")
                return@post
            }

            val result = addMemberToTrip(user.id, tripId)
            if (result) {
                call.respond(HttpStatusCode.OK, "Join request sent. Waiting for approval.")
            } else {
                call.respond(HttpStatusCode.OK, "You're already a member of this trip.")
            }
        }

        post("/trips/new") {
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

            try {
                val request = call.receive<CreateTripRequest>()

                val trip = createCompleteTrip(
                    ownerId = user.id,
                    destination = request.destination,
                    startDate = request.startDate,
                    endDate = request.endDate,
                    summary = request.summary,
                    accommodation = request.accommodation,
                    groupName = request.groupName,
                    reservationCode = request.reservationCode,
                    extraInfo = request.extraInfo
                )

                call.respond(HttpStatusCode.Created, trip)
            } catch (e: Exception) {
                e.printStackTrace()
                call.respondText(
                    "Failed to create trip: ${e.message}", status = HttpStatusCode.InternalServerError
                )
            }
        }

        delete("/trips/{groupCode}") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
            val groupCode = call.parameters["groupCode"]

            if (email == null || groupCode == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid request")
                return@delete
            }

            val user = findUserByEmail(email)
            if (user == null) {
                call.respond(HttpStatusCode.NotFound, "User not found")
                return@delete
            }

            val tripId = findTripByGroupCode(groupCode)
            if (tripId == null) {
                call.respond(HttpStatusCode.NotFound, "Trip not found")
                return@delete
            }

            // Check if user is trip owner - only owner can delete the trip
            if (!isUserTripOwner(user.id, tripId)) {
                call.respond(HttpStatusCode.Forbidden, "Only trip owner can delete the trip")
                return@delete
            }

            try {
                deleteTrip(tripId)
                call.respond(HttpStatusCode.OK, mapOf("message" to "Trip deleted successfully"))
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "Failed to delete trip"))
            }
        }
    }
}
