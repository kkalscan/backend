package ru.kkalscan.integrations.openrouter

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import ru.kkalscan.AppConfig
import ru.kkalscan.domain.VisionUnavailableException
import ru.kkalscan.domain.model.DishDto
import ru.kkalscan.domain.port.VisionClient
import java.util.Base64

class OpenRouterVisionClient(
    private val httpClient: HttpClient,
    private val apiKey: String = AppConfig.openRouterApiKey,
    private val model: String = AppConfig.openRouterModel,
    private val baseUrl: String = AppConfig.openRouterBaseUrl,
    private val appUrl: String = AppConfig.openRouterAppUrl,
    private val appName: String = AppConfig.openRouterAppName,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : VisionClient {

    override suspend fun analyzeFood(imageBytes: ByteArray): List<DishDto> {
        val base64 = Base64.getEncoder().encodeToString(imageBytes)
        val body = OpenRouterRequestBuilder.build(model, base64)

        val responseText = try {
            val response = httpClient.post("$baseUrl/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header("HTTP-Referer", appUrl)
                header("X-Title", appName)
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
            if (!response.status.isSuccess()) {
                val errBody = response.bodyAsText()
                throw VisionUnavailableException(
                    RuntimeException("OpenRouter HTTP ${response.status}: $errBody"),
                )
            }
            response.bodyAsText()
        } catch (e: VisionUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw VisionUnavailableException(e)
        }

        val content = parseOpenRouterResponse(responseText, json)
        return VisionResponseParser.parse(content)
    }
}
