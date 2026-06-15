package ru.kkalscan.integrations

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import ru.kkalscan.AppConfig
import ru.kkalscan.domain.port.VisionClient
import ru.kkalscan.integrations.openrouter.OpenRouterVisionClient

object VisionClientFactory {
    fun create(httpClient: HttpClient? = null): VisionClient =
        when (AppConfig.visionProvider.lowercase()) {
            "openrouter" -> {
                require(AppConfig.openRouterApiKey.isNotBlank()) {
                    "OPENROUTER_API_KEY is required when VISION_PROVIDER=openrouter"
                }
                OpenRouterVisionClient(
                    httpClient = httpClient ?: defaultHttpClient(),
                )
            }
            else -> StubVisionClient()
        }

    private fun defaultHttpClient(): HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }
}
