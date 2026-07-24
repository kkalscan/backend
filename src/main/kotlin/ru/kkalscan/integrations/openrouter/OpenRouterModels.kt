package ru.kkalscan.integrations.openrouter

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import ru.kkalscan.domain.model.DishDto
import ru.kkalscan.domain.model.WorkoutParseResult

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

internal object FeatureSearchIntentPrompt {
    val SYSTEM = """
        Ты классификатор запросов поиска в приложении подсчёта калорий.
        Пользователь вводит строку в поиск функций приложения (дневник, профиль, скан…).
        Верни ТОЛЬКО JSON: {"isFoodIntent":true} если человек ищет блюдо/продукт/еду чтобы записать калории,
        или {"isFoodIntent":false} если это навигация по функциям приложения, опечатка, мусор или не еда.
    """.trimIndent()

    fun userMessage(query: String): String = "Запрос поиска:\n${query.trim()}"
}

internal object WorkoutTextPrompt {
    val SYSTEM = """
        Ты фитнес-помощник для приложения подсчёта калорий в России.
        Пользователь описывает текстом тренировку и длительность: «бег 30 минут», «йога 45 мин», «плавание час».
        Оцени сожжённые калории для взрослого со средней физической формой (~70 кг), если вес не указан.
        Верни ТОЛЬКО JSON без markdown в формате:
        {"title":"краткое название на русском","burned_kcal":280,"duration_minutes":30}
        Если из описания нельзя понять активность — {"title":"","burned_kcal":0,"duration_minutes":null}
    """.trimIndent()

    fun userMessage(description: String): String =
        "Описание тренировки:\n${description.trim()}"
}

internal object DietitianWeekPrompt {
    val SYSTEM = """
        Ты диетолог-помощник для приложения подсчёта калорий в России.
        По сводке недели пользователя дай краткий дружелюбный разбор на русском.
        Не ставь диагнозов и не назначай лечение. Без морализаторства.
        Верни ТОЛЬКО JSON без markdown:
        {"headline":"одна короткая фраза","sections":[{"title":"...","body":"..."}]}
        Ровно 2–4 секции. Каждая секция: конкретный вывод по данным недели (ккал, БЖУ, блюда, активность).
    """.trimIndent()

    fun userMessage(weekJson: String): String =
        "Сводка недели (JSON):\n${weekJson.trim()}"
}

@Serializable
internal data class DishesEnvelope(val dishes: List<DishDto>)

@Serializable
internal data class FeatureSearchIntentEnvelope(val isFoodIntent: Boolean)

@Serializable
internal data class WorkoutParseEnvelope(
    val title: String,
    val burned_kcal: Int,
    val duration_minutes: Int? = null,
)

@Serializable
internal data class DietitianInsightEnvelope(
    val headline: String,
    val sections: List<DietitianSectionEnvelope> = emptyList(),
)

@Serializable
internal data class DietitianSectionEnvelope(
    val title: String,
    val body: String,
)

internal object VisionResponseParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(content: String): List<DishDto> {
        val payload = extractJsonPayload(content.trim())
        return json.decodeFromString<DishesEnvelope>(payload).dishes
    }

    fun parseWorkout(content: String): WorkoutParseResult {
        val payload = extractJsonPayload(content.trim())
        val envelope = json.decodeFromString<WorkoutParseEnvelope>(payload)
        return WorkoutParseResult(
            title = envelope.title.trim(),
            burnedKcal = envelope.burned_kcal,
            durationMinutes = envelope.duration_minutes,
        )
    }

    fun parseDietitianInsight(content: String): ru.kkalscan.domain.port.DietitianInsightResult {
        val payload = extractJsonPayload(content.trim())
        val envelope = json.decodeFromString<DietitianInsightEnvelope>(payload)
        val sections = envelope.sections
            .map {
                ru.kkalscan.domain.port.DietitianInsightSection(
                    title = it.title.trim(),
                    body = it.body.trim(),
                )
            }
            .filter { it.title.isNotBlank() && it.body.isNotBlank() }
            .take(4)
        require(envelope.headline.isNotBlank() && sections.size in 2..4) {
            "Invalid dietitian insight JSON"
        }
        return ru.kkalscan.domain.port.DietitianInsightResult(
            headline = envelope.headline.trim(),
            sections = sections,
        )
    }

    internal fun extractJsonPayload(raw: String): String {
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.get(1)?.trim()
        if (!fenced.isNullOrBlank()) return fenced

        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start >= 0 && end > start) return raw.substring(start, end + 1)

        return raw
    }
}

internal object FeatureSearchIntentParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(content: String): Boolean {
        val payload = VisionResponseParser.extractJsonPayload(content.trim())
        return runCatching {
            json.decodeFromString<FeatureSearchIntentEnvelope>(payload).isFoodIntent
        }.getOrDefault(false)
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

    fun buildFeatureSearchIntent(model: String, query: String): JsonObject = buildJsonObject {
        put("model", JsonPrimitive(model))
        put(
            "messages",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", JsonPrimitive("system"))
                        put("content", JsonPrimitive(FeatureSearchIntentPrompt.SYSTEM))
                    },
                )
                add(
                    buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("content", JsonPrimitive(FeatureSearchIntentPrompt.userMessage(query)))
                    },
                )
            },
        )
    }

    fun buildWorkoutText(model: String, description: String): JsonObject = buildJsonObject {
        put("model", JsonPrimitive(model))
        put(
            "messages",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", JsonPrimitive("system"))
                        put("content", JsonPrimitive(WorkoutTextPrompt.SYSTEM))
                    },
                )
                add(
                    buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("content", JsonPrimitive(WorkoutTextPrompt.userMessage(description)))
                    },
                )
            },
        )
    }

    fun buildDietitianWeek(model: String, weekJson: String): JsonObject = buildJsonObject {
        put("model", JsonPrimitive(model))
        put(
            "messages",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", JsonPrimitive("system"))
                        put("content", JsonPrimitive(DietitianWeekPrompt.SYSTEM))
                    },
                )
                add(
                    buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("content", JsonPrimitive(DietitianWeekPrompt.userMessage(weekJson)))
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
