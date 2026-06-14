package ru.kkalscan

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(Netty, port = AppConfig.port, module = Application::module).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                isLenient = true
            },
        )
    }

    routing {
        get("/health") {
            call.respond(
                mapOf(
                    "status" to "ok",
                    "version" to "0.1.0-SNAPSHOT",
                ),
            )
        }

        route("/api/v1") {
            // TODO: scanRoutes(), diaryRoutes(), subscriptionRoutes(), authRoutes(), paymentRoutes()
            get {
                call.respond(mapOf("service" to "kkalscan-api", "api" to "v1"))
            }
        }

        get("/privacy") {
            call.respondText("KkalScan privacy policy — placeholder", contentType = io.ktor.http.ContentType.Text.Plain)
        }
    }
}
