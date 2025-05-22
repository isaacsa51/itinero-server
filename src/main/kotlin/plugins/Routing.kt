package com.serranoie.server.plugins

import com.serranoie.server.routes.authRoutes
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        authRoutes()

        get("/") {
            call.respondText("Hello World!")
        }
    }
}
