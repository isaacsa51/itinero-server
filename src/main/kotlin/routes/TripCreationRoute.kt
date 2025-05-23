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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

fun Route.tripCreationRoute() {
    authenticate {
        post("/trips") {
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

                call.respond(trip)
            } catch (e: Exception) {
                e.printStackTrace()
                call.respondText("Failed to create trip: ${e.message}", status = HttpStatusCode.InternalServerError)
            }
        }
    }
}