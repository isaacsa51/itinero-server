package com.serranoie.server.routes

import com.serranoie.server.security.JwtConfig
import com.serranoie.server.models.AuthResponse
import com.serranoie.server.models.LoginRequest
import com.serranoie.server.models.RegisterRequest
import com.serranoie.server.models.DeleteAccountRequest
import com.serranoie.server.models.User
import com.serranoie.server.repository.createUser
import com.serranoie.server.repository.findUserByEmail
import com.serranoie.server.repository.deleteUser
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.mindrot.jbcrypt.BCrypt

fun Route.authRoutes() {
    route("/auth") {
        post("/register") {
            handleRegistration(call)
        }
        post("/login") {
            handleLogin(call)
        }
        authenticate {
            delete("/delete-account") {
                handleDeleteAccount(call)
            }
        }
    }
}

private suspend fun handleRegistration(call: ApplicationCall) {
    try {
        val req = call.receive<RegisterRequest>()

        val existingUser = findUserByEmail(req.email)
        if (existingUser != null) {
            call.respondText("Email already in use", status = HttpStatusCode.Conflict)
            return
        }

        val hashedPassword = BCrypt.hashpw(req.password, BCrypt.gensalt())
        val newUser = createUser(
            User(
                id = 0,
                name = req.name,
                surname = req.surname,
                phone = req.phone,
                email = req.email,
                passwordHash = hashedPassword
            )
        )

        respondWithToken(call, newUser.email, newUser.id, newUser.name, newUser.surname)

    } catch (e: ExposedSQLException) {
        if (e.message?.contains("unique constraint") == true || e.message?.contains("UNIQUE constraint failed") == true) {
            call.respondText("Email already in use", status = HttpStatusCode.Conflict)
        } else {
            handleServerError(call, e)
        }
    } catch (e: Exception) {
        handleServerError(call, e)
    }
}

private suspend fun handleLogin(call: ApplicationCall) {
    try {
        val req = call.receive<LoginRequest>()
        val user = findUserByEmail(req.email)

        if (user == null || !BCrypt.checkpw(req.password, user.passwordHash)) {
            call.respondText("Invalid credentials", status = HttpStatusCode.Unauthorized)
            return
        }

        respondWithToken(call, user.email, user.id, user.name, user.surname)
    } catch (e: Exception) {
        handleServerError(call, e)
    }
}

private suspend fun handleDeleteAccount(call: ApplicationCall) {
    try {
        val req = call.receive<DeleteAccountRequest>()
        val principal = call.principal<JWTPrincipal>()
        val email = principal?.payload?.getClaim("email")?.asString()

        if (email == null) {
            call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
            return
        }

        val userByEmail = findUserByEmail(email)

        if (userByEmail == null) {
            call.respondText("User not found", status = HttpStatusCode.NotFound)
            return
        }

        if (!BCrypt.checkpw(req.password, userByEmail.passwordHash)) {
            call.respondText("Invalid password", status = HttpStatusCode.Unauthorized)
            return
        }

        deleteUser(userByEmail.id)

        call.respondText("Account deleted successfully", status = HttpStatusCode.OK)
    } catch (e: Exception) {
        handleServerError(call, e)
    }
}

private suspend fun respondWithToken(
    call: ApplicationCall,
    email: String,
    userId: Int,
    userName: String,
    userLastName: String
) {
    val token = JwtConfig.generateToken(email)
    call.respond(AuthResponse(token, userId, userName, userLastName))
}

private suspend fun handleServerError(call: ApplicationCall, e: Exception) {
    e.printStackTrace()
    call.respondText("Internal server error", status = HttpStatusCode.InternalServerError)
}