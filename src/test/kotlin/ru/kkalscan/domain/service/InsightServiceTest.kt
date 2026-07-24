package ru.kkalscan.domain.service

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.kkalscan.TestFixtures
import ru.kkalscan.data.memory.InMemoryRepositories
import ru.kkalscan.domain.BadRequestException
import ru.kkalscan.domain.ForbiddenException
import ru.kkalscan.domain.model.DishDto
import ru.kkalscan.domain.model.MealType
import ru.kkalscan.domain.port.DiaryEntryRecord
import ru.kkalscan.domain.port.DietitianInsightResult
import ru.kkalscan.domain.port.DietitianInsightSection
import ru.kkalscan.domain.port.VisionClient
import ru.kkalscan.domain.port.WorkoutRecord
import ru.kkalscan.integrations.StubVisionClient
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.math.abs

class InsightServiceTest {
    private val repos = InMemoryRepositories()
    private val capturingVision = CapturingDietitianVisionClient()
    private val service = InsightServiceImpl(
        repos.diary,
        repos.workouts,
        capturingVision,
        repos.visionBudget,
    )

    @Test
    fun `pro forbidden when not pro`() = runTest {
        val actor = TestFixtures.guestActor().copy(isPro = false)
        assertFailsWith<ForbiddenException> {
            service.dietitianInsight(actor, LocalDate.of(2026, 7, 20), 180)
        }
    }

    @Test
    fun `requires at least 3 days with data`() = runTest {
        val actor = TestFixtures.guestActor().copy(isPro = true)
        seedDay(actor.deviceId, LocalDate.of(2026, 7, 20), 200)
        seedDay(actor.deviceId, LocalDate.of(2026, 7, 21), 300)
        assertFailsWith<BadRequestException> {
            service.dietitianInsight(actor, LocalDate.of(2026, 7, 20), 180)
        }
    }

    @Test
    fun `happy path returns stub insight from server week`() = runTest {
        val actor = TestFixtures.guestActor().copy(isPro = true)
        seedDay(actor.deviceId, LocalDate.of(2026, 7, 20), 400)
        seedDay(actor.deviceId, LocalDate.of(2026, 7, 21), 500)
        seedDay(actor.deviceId, LocalDate.of(2026, 7, 22), 450)

        val result = service.dietitianInsight(actor, LocalDate.of(2026, 7, 20), 180)
        assertEquals("2026-07-20", result.weekStart)
        assertTrue(result.headline.isNotBlank())
        assertTrue(result.sections.size in 2..4)
    }

    @Test
    fun `week json includes fiber macros and workouts`() = runTest {
        val actor = TestFixtures.guestActor().copy(isPro = true)
        seedDay(
            deviceId = actor.deviceId,
            date = LocalDate.of(2026, 7, 20),
            kcal = 500,
            protein = 40.0,
            fat = 15.0,
            carbs = 50.0,
            fiber = 12.5,
            dishName = "Гречка с курицей",
        )
        seedDay(actor.deviceId, LocalDate.of(2026, 7, 21), 450, fiber = 8.0)
        seedDay(actor.deviceId, LocalDate.of(2026, 7, 22), 480, fiber = 9.0)
        repos.workouts.insert(
            WorkoutRecord(
                id = UUID.randomUUID(),
                deviceId = actor.deviceId,
                userId = null,
                name = "Бег",
                kcal = 320,
                createdAt = Instant.parse("2026-07-20T16:00:00Z"),
            ),
        )

        service.dietitianInsight(actor, LocalDate.of(2026, 7, 20), 180)

        val week = Json.parseToJsonElement(capturingVision.lastWeekJson!!).jsonObject
        assertTrue(abs(week["avg_fiber_g"]!!.jsonPrimitive.double - 9.8) < 0.15)
        assertTrue(week["avg_protein_g"]!!.jsonPrimitive.double > 0)
        assertTrue(week["avg_fat_g"]!!.jsonPrimitive.double > 0)
        assertTrue(week["avg_carbs_g"]!!.jsonPrimitive.double > 0)
        assertEquals(1, week["workouts_count"]!!.jsonPrimitive.int)
        assertEquals(106, week["avg_burned_kcal"]!!.jsonPrimitive.int)

        val day0 = week["days"]!!.jsonArray.first().jsonObject
        assertEquals(12.5, day0["fiber_g"]!!.jsonPrimitive.double)
        assertEquals(40.0, day0["protein_g"]!!.jsonPrimitive.double)
        val workout = day0["workouts"]!!.jsonArray.single().jsonObject
        assertEquals("Бег", workout["name"]!!.jsonPrimitive.content)
        assertEquals(320, workout["kcal"]!!.jsonPrimitive.int)
    }

    private suspend fun seedDay(
        deviceId: UUID,
        date: LocalDate,
        kcal: Int,
        protein: Double = 10.0,
        fat: Double = 10.0,
        carbs: Double = 20.0,
        fiber: Double = 0.0,
        dishName: String = "Блюдо",
    ) {
        val createdAt = date.atTime(12, 0).toInstant(java.time.ZoneOffset.ofHours(3))
        repos.diary.insertEntry(
            DiaryEntryRecord(
                id = UUID.randomUUID(),
                deviceId = deviceId,
                userId = null,
                mealType = MealType.lunch,
                scanSessionId = null,
                totalKcal = kcal,
                createdAt = createdAt,
                dishes = emptyList(),
            ),
            listOf(
                DishDto(dishName, 200, kcal, protein, fat, carbs, fiber = fiber),
            ),
        )
    }

    private class CapturingDietitianVisionClient(
        private val delegate: VisionClient = StubVisionClient(),
    ) : VisionClient by delegate {
        var lastWeekJson: String? = null

        override suspend fun analyzeDietitianWeek(weekJson: String): DietitianInsightResult {
            lastWeekJson = weekJson
            return DietitianInsightResult(
                headline = "Тест",
                sections = listOf(
                    DietitianInsightSection("БЖУ", "ok"),
                    DietitianInsightSection("Тренировки", "ok"),
                ),
            )
        }
    }
}
