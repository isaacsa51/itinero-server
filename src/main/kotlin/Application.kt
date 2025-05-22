package com.serranoie.server

import com.serranoie.server.plugins.configureDatabases
import com.serranoie.server.routes.authRoutes
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {

    configureDatabases()

    install(ContentNegotiation) {
        json()
    }

    routing {
        authRoutes()
    }
}
