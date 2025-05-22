package com.serranoie.server.plugins

import com.serranoie.server.security.JwtConfig
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

//fun Application.configureAuthentication() {
//    install(Authentication) {
//        jwt {
//            verifier(JwtConfig.verifier())
//            validate {
//                val userId = it.payload.getClaim("userId").asInt()
//                if (userId != null) JWTPrincipal(it.payload) else null
//            }
//        }
//    }
//}