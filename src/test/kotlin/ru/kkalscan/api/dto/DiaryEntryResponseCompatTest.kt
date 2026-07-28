package ru.kkalscan.api.dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.kkalscan.domain.model.DishDto
import ru.kkalscan.domain.model.Encouragement
import ru.kkalscan.domain.model.EncouragementMood
import ru.kkalscan.domain.model.MealType
import ru.kkalscan.domain.port.CreateDiaryEntryResponse
import ru.kkalscan.domain.port.DiaryEntryDto
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiaryEntryResponseCompatTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    @Test
    fun toJson_includesEncouragementAsAdditiveField() {
        val response = CreateDiaryEntryResponse(
            entry = DiaryEntryDto(
                id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                createdAt = Instant.parse("2026-07-28T12:00:00Z"),
                mealType = MealType.lunch,
                totalKcal = 120,
                dishes = listOf(DishDto("Салат", 250, 120, 8.0, 4.0, 12.0, fiber = 9.0)),
            ),
            scansLeft = 2,
            encouragement = Encouragement(
                mood = EncouragementMood.good,
                text = "Записал — уже победа.",
                source = "rules",
            ),
        )

        val encoded = json.encodeToString(DiaryEntryResponse.serializer(), response.toJson())
        val obj = json.parseToJsonElement(encoded).jsonObject

        assertEquals(2, obj["scans_left"]!!.jsonPrimitive.content.toInt())
        assertNotNull(obj["entry"])
        val encouragement = obj["encouragement"]!!.jsonObject
        assertEquals("good", encouragement["mood"]!!.jsonPrimitive.content)
        assertEquals("Записал — уже победа.", encouragement["text"]!!.jsonPrimitive.content)
        assertEquals("rules", encouragement["source"]!!.jsonPrimitive.content)
    }

    @Test
    fun toJson_omitsEncouragementWhenNull_forOlderPayloadShape() {
        val response = CreateDiaryEntryResponse(
            entry = DiaryEntryDto(
                id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                createdAt = Instant.parse("2026-07-28T12:00:00Z"),
                mealType = MealType.lunch,
                totalKcal = 120,
                dishes = emptyList(),
            ),
            scansLeft = 2,
            encouragement = null,
        )

        val encoded = json.encodeToString(DiaryEntryResponse.serializer(), response.toJson())
        assertFalse(encoded.contains("encouragement"))
        val decoded = json.decodeFromString(DiaryEntryResponse.serializer(), encoded)
        assertNull(decoded.encouragement)
        assertEquals(2, decoded.scans_left)
    }

    @Test
    fun oldClientShape_stillDeserializesWhenEncouragementPresent() {
        val payload =
            """
            {
              "entry": {
                "id": "11111111-1111-1111-1111-111111111111",
                "created_at": "2026-07-28T12:00:00Z",
                "meal_type": "lunch",
                "total_kcal": 120,
                "dishes": []
              },
              "scans_left": 2,
              "encouragement": {
                "mood": "ok",
                "text": "Еда в дневнике — отлично.",
                "source": "rules"
              }
            }
            """.trimIndent()

        // Legacy-shaped model without encouragement field still works via ignoreUnknownKeys
        @kotlinx.serialization.Serializable
        data class LegacyDiaryEntryResponse(
            val entry: DiaryEntryJson,
            val scans_left: Int? = null,
        )

        val legacy = json.decodeFromString(LegacyDiaryEntryResponse.serializer(), payload)
        assertEquals(2, legacy.scans_left)
        assertEquals(120, legacy.entry.total_kcal)
        assertTrue(legacy.entry.id.isNotBlank())
    }
}
