package com.serranoie.server.routes

import com.serranoie.server.models.CreateItineraryItemRequest
import com.serranoie.server.models.UpdateItineraryItemRequest
import com.serranoie.server.models.ItineraryItem
import com.serranoie.server.models.Expense
import com.serranoie.server.repository.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import java.util.*

// Helper function to parse date/time from app format
private fun parseDateTimeFromApp(dateTimeString: String): Pair<String, String> {
    try {
        // Expected format: "01 July 2025, 12:00 PM"
        val parts = dateTimeString.split(", ")
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid date/time format")
        }

        val datePart = parts[0].trim() // "01 July 2025"
        val timePart = parts[1].trim() // "12:00 PM"

        // Parse date part to get ISO format (YYYY-MM-DD)
        val inputDateFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH)
        val outputDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val date = java.time.LocalDate.parse(datePart, inputDateFormatter)
        val isoDate = date.format(outputDateFormatter)

        // Parse time part to 24-hour format (HH:mm)
        val inputTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
        val outputTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val time = java.time.LocalTime.parse(timePart, inputTimeFormatter)
        val time24h = time.format(outputTimeFormatter)

        return Pair(isoDate, time24h)
    } catch (e: Exception) {
        throw IllegalArgumentException("Failed to parse date/time: ${e.message}")
    }
}

@Serializable
data class TodayOverviewResponse(
    val todayItinerary: List<ItineraryItem>,
    val yesterdayExpenses: List<Expense>,
    val date: String,
    val yesterdayDate: String
)

fun Route.itineraryRoutes() {
    authenticate {
        get("/trips/{groupCode}/itinerary") {
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

            if (!isTripMember(user.id, tripId)) {
                call.respond(HttpStatusCode.Forbidden, "You are not a member of this trip")
                return@get
            }

            val itineraryItems = getItineraryItemsByGroupCode(groupCode)
            call.respond(itineraryItems)
        }

        // Create a new itinerary item
        post("/trips/{groupCode}/itinerary") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
            val groupCode = call.parameters["groupCode"]

            println("=== CREATE ITINERARY ITEM DEBUG ===")
            println("Email: $email")
            println("Group Code: $groupCode")

            if (email == null || groupCode == null) {
                println("ERROR: Invalid request - email or groupCode is null")
                call.respond(HttpStatusCode.BadRequest, "Invalid request")
                return@post
            }

            val user = findUserByEmail(email)
            if (user == null) {
                println("ERROR: User not found for email: $email")
                call.respond(HttpStatusCode.NotFound, "User not found")
                return@post
            }
            println("User found: ${user.id} - ${user.name}")

            // Verify user is a member of the trip
            val tripId = findTripByGroupCode(groupCode)
            if (tripId == null) {
                println("ERROR: Trip not found for groupCode: $groupCode")
                call.respond(HttpStatusCode.NotFound, "Trip not found")
                return@post
            }
            println("Trip found: $tripId")

            if (!isTripMember(user.id, tripId)) {
                println("ERROR: User ${user.id} is not a member of trip $tripId")
                call.respond(HttpStatusCode.Forbidden, "You are not a member of this trip")
                return@post
            }
            println("User is confirmed trip member")

            try {
                println("Attempting to receive CreateItineraryItemRequest...")
                val requestBody = call.receiveText()
                println("Raw request body: $requestBody")

                // Try to parse manually to see what's happening
                val request = try {
                    kotlinx.serialization.json.Json.decodeFromString<CreateItineraryItemRequest>(requestBody)
                } catch (e: Exception) {
                    println("ERROR: Failed to parse request body: ${e.message}")
                    println("Exception type: ${e::class.simpleName}")
                    throw e
                }

                println("Successfully parsed request:")
                println("  name: ${request.name}")
                println("  description: ${request.description}")
                println("  date: ${request.date}")
                println("  time: ${request.time}")
                println("  location: ${request.location}")
                println("  isCompleted: ${request.isCompleted}")

                // Store date and time exactly as received from app
                println("Using original date/time format:")
                println("  date: ${request.date}")
                println("  time: ${request.time}")

                println("Creating itinerary item...")
                val createdItem = createItineraryItem(
                    groupCode = groupCode,
                    name = request.name,
                    description = request.description,
                    date = request.date,
                    time = request.time,
                    location = request.location
                )
                println("Successfully created item with ID: ${createdItem.id}")
                call.respond(HttpStatusCode.Created, createdItem)
            } catch (e: IllegalArgumentException) {
                println("ERROR: Invalid date/time format: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, "Invalid date/time format: ${e.message}")
            } catch (e: Exception) {
                println("ERROR: Exception during creation: ${e.message}")
                println("Exception type: ${e::class.simpleName}")
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, "Failed to create itinerary item: ${e.message}")
            }
        }

        // Get a specific itinerary item
        get("/trips/{groupCode}/itinerary/{itemId}") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
            val groupCode = call.parameters["groupCode"]
            val itemId = call.parameters["itemId"]?.toIntOrNull()

            if (email == null || groupCode == null || itemId == null) {
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

            if (!isTripMember(user.id, tripId)) {
                call.respond(HttpStatusCode.Forbidden, "You are not a member of this trip")
                return@get
            }

            val item = getItineraryItemById(itemId)
            if (item == null) {
                call.respond(HttpStatusCode.NotFound, "Itinerary item not found")
                return@get
            }

            // Verify item belongs to this trip
            if (item.groupCode != groupCode) {
                call.respond(HttpStatusCode.Forbidden, "Itinerary item does not belong to this trip")
                return@get
            }

            call.respond(item)
        }

        // Update an itinerary item
        put("/trips/{groupCode}/itinerary/{itemId}") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
            val groupCode = call.parameters["groupCode"]
            val itemId = call.parameters["itemId"]?.toIntOrNull()

            if (email == null || groupCode == null || itemId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid request")
                return@put
            }

            val user = findUserByEmail(email)
            if (user == null) {
                call.respond(HttpStatusCode.NotFound, "User not found")
                return@put
            }

            val tripId = findTripByGroupCode(groupCode)
            if (tripId == null) {
                call.respond(HttpStatusCode.NotFound, "Trip not found")
                return@put
            }

            if (!isTripMember(user.id, tripId)) {
                call.respond(HttpStatusCode.Forbidden, "You are not a member of this trip")
                return@put
            }

            val existingItem = getItineraryItemById(itemId)
            if (existingItem == null) {
                call.respond(HttpStatusCode.NotFound, "Itinerary item not found")
                return@put
            }

            // Verify item belongs to this trip
            if (existingItem.groupCode != groupCode) {
                call.respond(HttpStatusCode.Forbidden, "Itinerary item does not belong to this trip")
                return@put
            }

            try {
                val request = call.receive<UpdateItineraryItemRequest>()

                // Store date/time exactly as received from app
                val date = request.date
                val time = request.time

                val updated = updateItineraryItem(
                    itemId = itemId,
                    name = request.name,
                    description = request.description,
                    date = date,
                    time = time,
                    location = request.location,
                    isCompleted = request.isCompleted
                )

                if (updated) {
                    val updatedItem = getItineraryItemById(itemId)
                    call.respond(updatedItem!!)
                } else {
                    call.respond(HttpStatusCode.InternalServerError, "Failed to update itinerary item")
                }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, "Invalid date/time format: ${e.message}")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Failed to update itinerary item: ${e.message}")
            }
        }

        // Delete an itinerary item
        delete("/trips/{groupCode}/itinerary/{itemId}") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
            val groupCode = call.parameters["groupCode"]
            val itemId = call.parameters["itemId"]?.toIntOrNull()

            if (email == null || groupCode == null || itemId == null) {
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

            if (!isTripMember(user.id, tripId)) {
                call.respond(HttpStatusCode.Forbidden, "You are not a member of this trip")
                return@delete
            }

            val existingItem = getItineraryItemById(itemId)
            if (existingItem == null) {
                call.respond(HttpStatusCode.NotFound, "Itinerary item not found")
                return@delete
            }

            // Verify item belongs to this trip
            if (existingItem.groupCode != groupCode) {
                call.respond(HttpStatusCode.Forbidden, "Itinerary item does not belong to this trip")
                return@delete
            }

            val deleted = deleteItineraryItem(itemId)
            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.InternalServerError, "Failed to delete itinerary item")
            }
        }

        // Mark an itinerary item as completed/incomplete
        patch("/trips/{groupCode}/itinerary/{itemId}/complete") {
            val principal = call.principal<JWTPrincipal>()
            val email = principal?.payload?.getClaim("email")?.asString()
            val groupCode = call.parameters["groupCode"]
            val itemId = call.parameters["itemId"]?.toIntOrNull()

            if (email == null || groupCode == null || itemId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid request")
                return@patch
            }

            val user = findUserByEmail(email)
            if (user == null) {
                call.respond(HttpStatusCode.NotFound, "User not found")
                return@patch
            }

            val tripId = findTripByGroupCode(groupCode)
            if (tripId == null) {
                call.respond(HttpStatusCode.NotFound, "Trip not found")
                return@patch
            }

            if (!isTripMember(user.id, tripId)) {
                call.respond(HttpStatusCode.Forbidden, "You are not a member of this trip")
                return@patch
            }

            val existingItem = getItineraryItemById(itemId)
            if (existingItem == null) {
                call.respond(HttpStatusCode.NotFound, "Itinerary item not found")
                return@patch
            }

            // Verify item belongs to this trip
            if (existingItem.groupCode != groupCode) {
                call.respond(HttpStatusCode.Forbidden, "Itinerary item does not belong to this trip")
                return@patch
            }

            val completed = !existingItem.isCompleted // Toggle completion status
            val updated = markItineraryItemCompleted(itemId, completed)

            if (updated) {
                val updatedItem = getItineraryItemById(itemId)
                call.respond(updatedItem!!)
            } else {
                call.respond(HttpStatusCode.InternalServerError, "Failed to update completion status")
            }
        }

        // New endpoint for today's overview (today's itinerary + yesterday's expenses)
        get("/trips/{groupCode}/today-overview") {
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

            if (!isTripMember(user.id, tripId)) {
                call.respond(HttpStatusCode.Forbidden, "You are not a member of this trip")
                return@get
            }

            try {
                // Get today's date in the format used by the app
                val today = LocalDate.now()
                val yesterday = today.minusDays(1)

                // Format dates to match the app's format (e.g., "2025-01-15")
                val todayStr = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                val yesterdayStr = yesterday.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                // Convert today's date to the format used in itinerary items ("31 July 2025")
                val appDateFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH)
                val todayAppFormat = today.format(appDateFormatter)

                // Get today's itinerary items using the app's date format
                val todayItineraryItems = getItineraryItemsByGroupCodeAndDate(groupCode, todayAppFormat)

                // Get yesterday's expenses (expenses use ISO format)
                val yesterdayExpenses = getTripExpensesByDate(tripId, yesterdayStr)

                val response = TodayOverviewResponse(
                    todayItinerary = todayItineraryItems,
                    yesterdayExpenses = yesterdayExpenses,
                    date = todayStr,
                    yesterdayDate = yesterdayStr
                )

                call.respond(response)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Failed to fetch today's overview: ${e.message}")
            }
        }
    }
}
