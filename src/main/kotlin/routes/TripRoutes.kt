package com.serranoie.server.routes

import com.serranoie.server.repository.Trips
import com.serranoie.server.repository.addMemberToTrip
import com.serranoie.server.repository.findUserByEmail
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.tripAssociationRoutes() {
    authenticate {
        post("/trips/{tripId}/join") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
            val tripId = call.parameters["tripId"]?.toIntOrNull()

            if (email == null || tripId == null) {
                call.respondText("Invalid request", status = HttpStatusCode.BadRequest)
                return@post
            }

            val user = findUserByEmail(email)
            if (user == null) {
                call.respondText("User not found", status = HttpStatusCode.NotFound)
                return@post
            }

            val tripExists = transaction {
                Trips.selectAll().where { Trips.id eq tripId }.count() > 0
            }

            if (!tripExists) {
                call.respond(HttpStatusCode.NotFound, "Trip not found")
                return@post
            }

            addMemberToTrip(user.id, tripId)
            call.respond(HttpStatusCode.OK, "User added to trip $tripId")
        }
    }
}