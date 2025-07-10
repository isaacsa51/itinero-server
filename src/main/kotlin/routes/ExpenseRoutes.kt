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
        post("/trips/{groupCode}/expenses") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
                ?: return@post call.respondText(
                    "Unauthorized", status = HttpStatusCode.Unauthorized
                )

            val user = findUserByEmail(email)
                ?: return@post call.respondText(
                    "User not found", status = HttpStatusCode.NotFound
                )

            val groupCode = call.parameters["groupCode"]
                ?: return@post call.respondText(
                    "Invalid group code", status = HttpStatusCode.BadRequest
                )

            val tripId = findTripByGroupCode(groupCode)
                ?: return@post call.respondText(
                    "Trip not found", status = HttpStatusCode.NotFound
                )

            try {
                val request = call.receive<CreateExpenseRequest>()

                // Verify user is a trip member
                if (!isTripMember(user.id, tripId) && !isUserTripOwner(user.id, tripId)) {
                    call.respondText(
                        "You must be a trip member to add expenses",
                        status = HttpStatusCode.Forbidden
                    )
                    return@post
                }

                val expense = createExpense(request, tripId, user.id)
                call.respond(HttpStatusCode.Created, expense)
            } catch (e: IllegalArgumentException) {
                call.respondText(e.message ?: "Invalid request", status = HttpStatusCode.BadRequest)
            } catch (e: Exception) {
                e.printStackTrace()
                call.respondText("Failed to create expense", status = HttpStatusCode.InternalServerError)
            }
        }

        get("/trips/{groupCode}/expenses") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
                ?: return@get call.respondText(
                    "Unauthorized", status = HttpStatusCode.Unauthorized
                )

            val user = findUserByEmail(email)
                ?: return@get call.respondText(
                    "User not found", status = HttpStatusCode.NotFound
                )

            val groupCode = call.parameters["groupCode"]
                ?: return@get call.respondText(
                    "Invalid group code", status = HttpStatusCode.BadRequest
                )

            val tripId = findTripByGroupCode(groupCode)
                ?: return@get call.respondText(
                    "Trip not found", status = HttpStatusCode.NotFound
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

        get("/trips/{groupCode}/expenses/summary") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
                ?: return@get call.respondText(
                    "Unauthorized", status = HttpStatusCode.Unauthorized
                )

            val user = findUserByEmail(email)
                ?: return@get call.respondText(
                    "User not found", status = HttpStatusCode.NotFound
                )

            val groupCode = call.parameters["groupCode"]
                ?: return@get call.respondText(
                    "Invalid group code", status = HttpStatusCode.BadRequest
                )

            val tripId = findTripByGroupCode(groupCode)
                ?: return@get call.respondText(
                    "Trip not found", status = HttpStatusCode.NotFound
                )

            // Verify user is a trip member
            if (!isTripMember(user.id, tripId) && !isUserTripOwner(user.id, tripId)) {
                call.respondText(
                    "You must be a trip member to view expense summary",
                    status = HttpStatusCode.Forbidden
                )
                return@get
            }

            val summary = getUserExpenseSummary(tripId, user.id)
            call.respond(summary)
        }

        get("/trips/{groupCode}/expenses/summary/all") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
                ?: return@get call.respondText(
                    "Unauthorized", status = HttpStatusCode.Unauthorized
                )

            val user = findUserByEmail(email)
                ?: return@get call.respondText(
                    "User not found", status = HttpStatusCode.NotFound
                )

            val groupCode = call.parameters["groupCode"]
                ?: return@get call.respondText(
                    "Invalid group code", status = HttpStatusCode.BadRequest
                )

            val tripId = findTripByGroupCode(groupCode)
                ?: return@get call.respondText(
                    "Trip not found", status = HttpStatusCode.NotFound
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

        patch("/trips/{groupCode}/expenses/{expenseId}/complete") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
                ?: return@patch call.respondText(
                    "Unauthorized", status = HttpStatusCode.Unauthorized
                )

            val user = findUserByEmail(email)
                ?: return@patch call.respondText(
                    "User not found", status = HttpStatusCode.NotFound
                )

            val groupCode = call.parameters["groupCode"]
                ?: return@patch call.respondText(
                    "Invalid group code", status = HttpStatusCode.BadRequest
                )

            val expenseId = call.parameters["expenseId"]?.toIntOrNull()
                ?: return@patch call.respondText(
                    "Invalid expense ID", status = HttpStatusCode.BadRequest
                )

            val tripId = findTripByGroupCode(groupCode)
                ?: return@patch call.respondText(
                    "Trip not found", status = HttpStatusCode.NotFound
                )

            // Verify user is a trip member
            if (!isTripMember(user.id, tripId) && !isUserTripOwner(user.id, tripId)) {
                call.respondText(
                    "You must be a trip member to complete expenses",
                    status = HttpStatusCode.Forbidden
                )
                return@patch
            }

            if (markExpenseAsCompleted(expenseId, user.id)) {
                call.respondText("Expense marked as completed", status = HttpStatusCode.OK)
            } else {
                call.respondText(
                    "Failed to mark expense as completed. You might not be the payer.",
                    status = HttpStatusCode.Forbidden
                )
            }
        }

        put("/trips/{groupCode}/expenses/{expenseId}") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
                ?: return@put call.respondText(
                    "Unauthorized", status = HttpStatusCode.Unauthorized
                )

            val user = findUserByEmail(email)
                ?: return@put call.respondText(
                    "User not found", status = HttpStatusCode.NotFound
                )

            val groupCode = call.parameters["groupCode"]
                ?: return@put call.respondText(
                    "Invalid group code", status = HttpStatusCode.BadRequest
                )

            val expenseId = call.parameters["expenseId"]?.toIntOrNull()
                ?: return@put call.respondText(
                    "Invalid expense ID", status = HttpStatusCode.BadRequest
                )

            val tripId = findTripByGroupCode(groupCode)
                ?: return@put call.respondText(
                    "Trip not found", status = HttpStatusCode.NotFound
                )

            // Verify user is a trip member
            if (!isTripMember(user.id, tripId) && !isUserTripOwner(user.id, tripId)) {
                call.respondText(
                    "You must be a trip member to update expenses",
                    status = HttpStatusCode.Forbidden
                )
                return@put
            }

            try {
                val updateRequest = call.receive<UpdateExpenseRequest>()
                val updatedExpense = updateExpense(expenseId, updateRequest, user.id)

                if (updatedExpense != null) {
                    call.respond(updatedExpense)
                } else {
                    call.respondText(
                        "Failed to update expense. You might not be the payer or trip owner, or expense not found.",
                        status = HttpStatusCode.Forbidden
                    )
                }
            } catch (e: IllegalArgumentException) {
                call.respondText(e.message ?: "Invalid request", status = HttpStatusCode.BadRequest)
            } catch (e: Exception) {
                e.printStackTrace()
                call.respondText("Failed to update expense", status = HttpStatusCode.InternalServerError)
            }
        }

        delete("/trips/{groupCode}/expenses/{expenseId}") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
                ?: return@delete call.respondText(
                    "Unauthorized", status = HttpStatusCode.Unauthorized
                )

            val user = findUserByEmail(email)
                ?: return@delete call.respondText(
                    "User not found", status = HttpStatusCode.NotFound
                )

            val groupCode = call.parameters["groupCode"]
                ?: return@delete call.respondText(
                    "Invalid group code", status = HttpStatusCode.BadRequest
                )

            val expenseId = call.parameters["expenseId"]?.toIntOrNull()
                ?: return@delete call.respondText(
                    "Invalid expense ID", status = HttpStatusCode.BadRequest
                )

            val tripId = findTripByGroupCode(groupCode)
                ?: return@delete call.respondText(
                    "Trip not found", status = HttpStatusCode.NotFound
                )

            // Verify user is a trip member
            if (!isTripMember(user.id, tripId) && !isUserTripOwner(user.id, tripId)) {
                call.respondText(
                    "You must be a trip member to delete expenses",
                    status = HttpStatusCode.Forbidden
                )
                return@delete
            }

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
