package com.serranoie.server

import com.serranoie.server.plugins.configureDatabases
import com.serranoie.server.routes.authRoutes
import com.serranoie.server.routes.homeRoutes
import com.serranoie.server.routes.tripAssociationRoutes
import com.serranoie.server.security.JwtConfig
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
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

    install(Authentication) {
        jwt {
            verifier(JwtConfig.verifier)
            validate {
                val email = it.payload.getClaim("email").asString()
                if (email != null) JWTPrincipal(it.payload) else null
            }
        }
    }

    routing {
        authRoutes()

        authenticate {
            homeRoutes()
            tripAssociationRoutes()
        }
    }
}