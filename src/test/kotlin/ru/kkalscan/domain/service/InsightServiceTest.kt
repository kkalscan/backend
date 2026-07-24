package ru.kkalscan.domain.service

import kotlinx.coroutines.test.runTest
import ru.kkalscan.TestFixtures
import ru.kkalscan.data.memory.InMemoryRepositories
import ru.kkalscan.domain.BadRequestException
import ru.kkalscan.domain.ForbiddenException
import ru.kkalscan.domain.model.Actor
import ru.kkalscan.domain.model.DishDto
import ru.kkalscan.domain.model.MealType
import ru.kkalscan.domain.port.DiaryEntryRecord
import ru.kkalscan.integrations.StubVisionClient
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InsightServiceTest {
    private val repos = InMemoryRepositories()
    private val service = InsightServiceImpl(
        repos.diary,
        repos.workouts,
        StubVisionClient(),
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

    private suspend fun seedDay(deviceId: UUID, date: LocalDate, kcal: Int) {
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
                DishDto("Блюдо", 200, kcal, 10.0, 10.0, 20.0),
            ),
        )
    }
}
