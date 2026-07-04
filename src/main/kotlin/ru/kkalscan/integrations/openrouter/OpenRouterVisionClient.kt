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
                val message = openRouterErrorMessage(response.status.value, errBody, model)
                throw VisionUnavailableException(message, RuntimeException("OpenRouter HTTP ${response.status}: $errBody"))
            }
            response.bodyAsText()
        } catch (e: VisionUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw VisionUnavailableException(cause = e)
        }

        val content = parseOpenRouterResponse(responseText, json)
        return VisionResponseParser.parse(content)
    }

    override suspend fun analyzeDescription(description: String): List<DishDto> {
        val body = OpenRouterRequestBuilder.buildText(model, description)
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
                val message = openRouterErrorMessage(response.status.value, errBody, model)
                throw VisionUnavailableException(message, RuntimeException("OpenRouter HTTP ${response.status}: $errBody"))
            }
            response.bodyAsText()
        } catch (e: VisionUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw VisionUnavailableException(cause = e)
        }

        val content = parseOpenRouterResponse(responseText, json)
        return VisionResponseParser.parse(content)
    }

    private fun openRouterErrorMessage(status: Int, body: String, model: String): String = when (status) {
        401 -> "OpenRouter: неверный API-ключ. Проверьте OPENROUTER_API_KEY."
        402 -> "OpenRouter: недостаточно средств на балансе."
        404 -> "OpenRouter: модель $model не найдена. Обновите OPENROUTER_MODEL."
        else -> if (body.contains("model", ignoreCase = true)) {
            "OpenRouter: проблема с моделью $model."
        } else {
            "Не удалось распознать фото, попробуйте ещё раз"
        }
    }
}
