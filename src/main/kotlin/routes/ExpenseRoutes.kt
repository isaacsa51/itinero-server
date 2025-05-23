package com.serranoie.server.routes

import com.serranoie.server.models.*
import com.serranoie.server.repository.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.expenseRoutes() {
    authenticate {
        // Create expense
        post("/expenses") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
                ?: return@post call.respondText(
                    "Unauthorized", status = HttpStatusCode.Unauthorized
                )

            val user = findUserByEmail(email)
                ?: return@post call.respondText(
                    "User not found", status = HttpStatusCode.NotFound
                )

            try {
                val request = call.receive<CreateExpenseRequest>()

                // Verify user is a trip member
                if (!isTripMember(user.id, request.tripId) && !isUserTripOwner(user.id, request.tripId)) {
                    call.respondText(
                        "You must be a trip member to add expenses",
                        status = HttpStatusCode.Forbidden
                    )
                    return@post
                }

                val expense = createExpense(request, user.id)
                call.respond(HttpStatusCode.Created, expense)
            } catch (e: IllegalArgumentException) {
                call.respondText(e.message ?: "Invalid request", status = HttpStatusCode.BadRequest)
            } catch (e: Exception) {
                e.printStackTrace()
                call.respondText("Failed to create expense", status = HttpStatusCode.InternalServerError)
            }
        }

        // Get expenses for a trip
        get("/trips/{tripId}/expenses") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
                ?: return@get call.respondText(
                    "Unauthorized", status = HttpStatusCode.Unauthorized
                )

            val user = findUserByEmail(email)
                ?: return@get call.respondText(
                    "User not found", status = HttpStatusCode.NotFound
                )

            val tripId = call.parameters["tripId"]?.toIntOrNull()
                ?: return@get call.respondText(
                    "Invalid trip ID", status = HttpStatusCode.BadRequest
                )

            // Verify user is a trip member
            if (!isTripMember(user.id, tripId) && !isUserTripOwner(user.id, tripId)) {
                call.respondText(
                    "You must be a trip member to view expenses",
                    status = HttpStatusCode.Forbidden
                )
                return@get
            }

            val expenses = getTripExpenses(tripId)
            call.respond(expenses)
        }

        // Get expense summary for a trip
        get("/trips/{tripId}/expenses/summary") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
                ?: return@get call.respondText(
                    "Unauthorized", status = HttpStatusCode.Unauthorized
                )

            val user = findUserByEmail(email)
                ?: return@get call.respondText(
                    "User not found", status = HttpStatusCode.NotFound
                )

            val tripId = call.parameters["tripId"]?.toIntOrNull()
                ?: return@get call.respondText(
                    "Invalid trip ID", status = HttpStatusCode.BadRequest
                )

            // Verify user is a trip member
            if (!isTripMember(user.id, tripId) && !isUserTripOwner(user.id, tripId)) {
                call.respondText(
                    "You must be a trip member to view expense summary",
                    status = HttpStatusCode.Forbidden
                )
                return@get
            }

            val summary = getExpenseSummary(tripId)
            call.respond(summary)
        }

        // Mark expense as completed
        post("/expenses/{id}/complete") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
                ?: return@post call.respondText(
                    "Unauthorized", status = HttpStatusCode.Unauthorized
                )

            val user = findUserByEmail(email)
                ?: return@post call.respondText(
                    "User not found", status = HttpStatusCode.NotFound
                )

            val expenseId = call.parameters["id"]?.toIntOrNull()
                ?: return@post call.respondText(
                    "Invalid expense ID", status = HttpStatusCode.BadRequest
                )

            if (markExpenseAsCompleted(expenseId, user.id)) {
                call.respondText("Expense marked as completed", status = HttpStatusCode.OK)
            } else {
                call.respondText(
                    "Failed to mark expense as completed. You might not be the payer.",
                    status = HttpStatusCode.Forbidden
                )
            }
        }

        // Delete expense
        delete("/expenses/{id}") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
                ?: return@delete call.respondText(
                    "Unauthorized", status = HttpStatusCode.Unauthorized
                )

            val user = findUserByEmail(email)
                ?: return@delete call.respondText(
                    "User not found", status = HttpStatusCode.NotFound
                )

            val expenseId = call.parameters["id"]?.toIntOrNull()
                ?: return@delete call.respondText(
                    "Invalid expense ID", status = HttpStatusCode.BadRequest
                )

            if (deleteExpense(expenseId, user.id)) {
                call.respondText("Expense deleted successfully", status = HttpStatusCode.OK)
            } else {
                call.respondText(
                    "Failed to delete expense. You might not be the payer or trip owner.",
                    status = HttpStatusCode.Forbidden
                )
            }
        }
    }
}
