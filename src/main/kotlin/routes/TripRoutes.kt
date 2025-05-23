package com.serranoie.server.routes

import com.serranoie.server.models.CreateTripRequest
import com.serranoie.server.repository.Trips
import com.serranoie.server.repository.addMemberToTrip
import com.serranoie.server.repository.createCompleteTrip
import com.serranoie.server.repository.findUserByEmail
import com.serranoie.server.repository.findTripByGroupCode
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
        // Using groupCode in URL instead of numeric ID
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

            // Find trip by group code
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

        // Route for creating a new trip
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

                // Calculate total days between start and end dates
                val formatter = DateTimeFormatter.ISO_DATE
                val startDate = LocalDate.parse(request.startDate, formatter)
                val endDate = LocalDate.parse(request.endDate, formatter)
                val totalDays = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1

                // Create the trip using the createCompleteTrip function
                val trip = createCompleteTrip(
                    ownerId = user.id,
                    destination = request.destination,
                    startDate = request.startDate,
                    endDate = request.endDate,
                    totalDays = totalDays,
                    summary = request.summary,
                    accommodation = request.accommodation,
                    reservationCode = request.reservationCode,
                    extraInfo = request.extraInfo,
                    additionalInfo = request.additionalInfo
                )

                call.respond(HttpStatusCode.Created, trip)
            } catch (e: Exception) {
                e.printStackTrace()
                call.respondText(
                    "Failed to create trip: ${e.message}",
                    status = HttpStatusCode.InternalServerError
                )
            }
        }
    }
}
