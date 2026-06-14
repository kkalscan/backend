package ru.kkalscan

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import kotlinx.serialization.json.Json
import ru.kkalscan.plugins.configureStatusPages
import ru.kkalscan.routes.configureRouting

fun main() {
    embeddedServer(Netty, port = AppConfig.port, module = Application::module).start(wait = true)
}

fun Application.module() {
    val module = AppModule()

    environment.log.info(
        "Vision: provider={}, model={}",
        AppConfig.visionProvider,
        if (AppConfig.visionProvider == "openrouter") AppConfig.openRouterModel else "n/a",
    )

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
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-Device-Id")
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
    }
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
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-Device-Id")
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
    }
    configureStatusPages()
    configureRouting(customModule)
}
