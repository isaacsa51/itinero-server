package com.serranoie.server

import com.serranoie.server.plugins.configureDatabases
import com.serranoie.server.routes.authRoutes
import com.serranoie.server.routes.expenseRoutes
import com.serranoie.server.routes.tripAssociationRoutes
import com.serranoie.server.routes.tripSettingsRoutes
import com.serranoie.server.security.JwtConfig
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import java.util.logging.LoggingMXBean

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureDatabases()

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
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
            // Trip association and group management endpoints
            tripAssociationRoutes()

            // Trip settings and member management endpoints
            tripSettingsRoutes()
            
            // Expense management endpoints
            expenseRoutes()
        }
    }
}
