package ru.kkalscan.integrations.openrouter

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import ru.kkalscan.domain.model.DishDto

internal object FoodVisionPrompt {
    val TEXT = """
        Ты анализируешь фото еды для приложения подсчёта калорий в России.
        Верни ТОЛЬКО JSON без markdown в формате:
        {"dishes":[{"name":"название на русском","grams":300,"kcal":180,"protein":8.5,"fat":6.2,"carbs":22.1,"fiber":4.0}]}
        Оцени порции реалистично. Если на фото несколько блюд — перечисли все.
        Если еды не видно — {"dishes":[]}
    """.trimIndent()
}

internal object FoodTextPrompt {
    val SYSTEM = """
        Ты диетолог-помощник для приложения подсчёта калорий в России.
        Пользователь описывает текстом или голосом, что съел: блюда, примерный объём, вес или «тарелка».
        Оцени порции реалистично по описанию. Если несколько блюд — перечисли все.
        Верни ТОЛЬКО JSON без markdown в формате:
        {"dishes":[{"name":"название на русском","grams":300,"kcal":180,"protein":8.5,"fat":6.2,"carbs":22.1,"fiber":4.0}]}
        Если из описания нельзя понять еду — {"dishes":[]}
    """.trimIndent()

    fun userMessage(description: String): String =
        "Описание пользователя:\n${description.trim()}"
}

@Serializable
internal data class DishesEnvelope(val dishes: List<DishDto>)

internal object VisionResponseParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(content: String): List<DishDto> {
        val payload = extractJsonPayload(content.trim())
        return json.decodeFromString<DishesEnvelope>(payload).dishes
    }

    private fun extractJsonPayload(raw: String): String {
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.get(1)?.trim()
        if (!fenced.isNullOrBlank()) return fenced

        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start >= 0 && end > start) return raw.substring(start, end + 1)

        return raw
    }
}

internal object OpenRouterRequestBuilder {
    fun build(model: String, imageBase64: String): JsonObject = buildJsonObject {
        put("model", JsonPrimitive(model))
        put(
            "messages",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put(
                            "content",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("type", JsonPrimitive("text"))
                                        put("text", JsonPrimitive(FoodVisionPrompt.TEXT))
                                    },
                                )
                                add(
                                    buildJsonObject {
                                        put("type", JsonPrimitive("image_url"))
                                        put(
                                            "image_url",
                                            buildJsonObject {
                                                put(
                                                    "url",
                                                    JsonPrimitive("data:image/jpeg;base64,$imageBase64"),
                                                )
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
    }

    fun buildText(model: String, description: String): JsonObject = buildJsonObject {
        put("model", JsonPrimitive(model))
        put(
            "messages",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", JsonPrimitive("system"))
                        put("content", JsonPrimitive(FoodTextPrompt.SYSTEM))
                    },
                )
                add(
                    buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("content", JsonPrimitive(FoodTextPrompt.userMessage(description)))
                    },
                )
            },
        )
    }
}

@Serializable
internal data class OpenRouterChatResponse(
    val choices: List<OpenRouterChoice> = emptyList(),
)

@Serializable
internal data class OpenRouterChoice(val message: OpenRouterMessage)

@Serializable
internal data class OpenRouterMessage(val content: String? = null)

@Serializable
internal data class OpenRouterErrorResponse(val error: OpenRouterError? = null)

@Serializable
internal data class OpenRouterError(val message: String? = null)

internal fun parseOpenRouterResponse(body: String, json: Json): String {
    val error = runCatching { json.decodeFromString<OpenRouterErrorResponse>(body).error?.message }.getOrNull()
    if (error != null) error("OpenRouter: $error")

    val response = json.decodeFromString<OpenRouterChatResponse>(body)
    return response.choices.firstOrNull()?.message?.content
        ?: error("OpenRouter: empty response")
}
