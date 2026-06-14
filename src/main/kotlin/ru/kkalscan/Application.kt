package ru.kkalscan

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json
import ru.kkalscan.plugins.configureStatusPages
import ru.kkalscan.routes.configureRouting

fun main() {
    embeddedServer(Netty, port = AppConfig.port, module = Application::module).start(wait = true)
}

fun Application.module() {
    val module = AppModule()

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                isLenient = true
            },
        )
    }
    install(CallLogging)
    configureStatusPages()
    configureRouting(module)
}

fun Application.testModule(customModule: AppModule = AppModule()) {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                isLenient = true
            },
        )
    }
    configureStatusPages()
    configureRouting(customModule)
}
