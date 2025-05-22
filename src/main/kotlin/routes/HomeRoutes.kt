package com.serranoie.server.routes

import com.serranoie.server.models.HomeResponse
import com.serranoie.server.repository.findAllTripsForUser
import com.serranoie.server.repository.findTripForUser
import com.serranoie.server.repository.findUserByEmail
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.homeRoutes() {
    authenticate {
        get("/home") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()

            if (email == null) {
                call.respondText("Email not found", status = HttpStatusCode.Unauthorized)
                return@get
            }

            val user = findUserByEmail(email)
            if (user == null) {
                call.respondText("User not found", status = HttpStatusCode.NotFound)
                return@get
            }

            // Get all trips for the user (for future multi-trip support)
            val allTrips = findAllTripsForUser(user.id)
            if (allTrips.isEmpty()) {
                call.respondText("No trips found", status = HttpStatusCode.NotFound)
                return@get
            }
            
            // For now, we just return the first trip as before
            val currentTrip = allTrips.first()

            call.respond(HomeResponse(currentTrip = currentTrip, allTrips = allTrips))
        }
    }
}