package ru.kkalscan.domain.service

import kotlinx.coroutines.test.runTest
import ru.kkalscan.TestFixtures
import ru.kkalscan.data.memory.InMemoryRepositories
import ru.kkalscan.domain.LimitHitException
import ru.kkalscan.domain.model.MealType
import ru.kkalscan.domain.port.DiaryService
import ru.kkalscan.integrations.StubVisionClient
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScanAndDiaryServiceTest {
    private val repos = InMemoryRepositories()
    private val quotaService = QuotaServiceImpl(repos.quotas, repos.devices, repos.users)
    private val scanService = ScanServiceImpl(
        quotaService,
        StubVisionClient(),
        repos.scanSessions,
        repos.visionBudget,
    )
    private val diaryService = DiaryServiceImpl(repos.diary, repos.workouts, quotaService, repos.scanSessions)
    private val date = LocalDate.of(2026, 6, 14)
    private val actor = TestFixtures.guestActor()
    private val photo = ByteArray(1024) { 0xFF.toByte() }

    @Test
    fun `scan does not consume quota until diary entry`() = runTest {
        val scan = scanService.analyzePhoto(actor, photo, date, 180)
        assertEquals(3, scan.scansLeft)
        assertEquals(3, quotaService.getScansLeft(actor, date))
    }

    @Test
    fun `diary entry with edited dishes from scan uses client values`() = runTest {
        val scan = scanService.analyzePhoto(actor, photo, date, 180)
        val edited = scan.dishes.map { it.copy(grams = 150, kcal = 120) }
        val entry = diaryService.addEntry(
            actor,
            DiaryService.CreateDiaryEntryRequest(
                mealType = MealType.lunch,
                scanId = scan.scanId,
                dishes = edited,
            ),
            date,
        )
        assertEquals(120, entry.entry.totalKcal)
        assertEquals(150, entry.entry.dishes.single().grams)
        assertEquals(2, entry.scansLeft)
    }

    @Test
    fun `diary entry consumes scan quota`() = runTest {
        val scan = scanService.analyzePhoto(actor, photo, date, 180)
        val entry = diaryService.addEntry(
            actor,
            DiaryService.CreateDiaryEntryRequest(MealType.lunch, scanId = scan.scanId),
            date,
        )
        assertEquals(2, entry.scansLeft)
    }

    @Test
    fun `diary entry with manual dishes consumes quota`() = runTest {
        val entry = diaryService.addEntry(
            actor,
            DiaryService.CreateDiaryEntryRequest(
                mealType = MealType.breakfast,
                dishes = listOf(
                    ru.kkalscan.domain.model.DishDto("Овсянка", 200, 150, 5.0, 3.0, 27.0),
                ),
            ),
            date,
        )

        assertEquals(150, entry.entry.totalKcal)
        assertEquals(2, entry.scansLeft)
    }

    @Test
    fun `fourth scan blocked when quota exhausted`() = runTest {
        repeat(3) {
            val scan = scanService.analyzePhoto(actor, photo, date, 180)
            diaryService.addEntry(
                actor,
                DiaryService.CreateDiaryEntryRequest(MealType.lunch, scanId = scan.scanId),
                date,
            )
        }
        assertFailsWith<LimitHitException> {
            scanService.analyzePhoto(actor, photo, date, 180)
        }
    }

    @Test
    fun `workout reduces net kcal in diary day`() = runTest {
        val tzOffsetMinutes = 180
        val today = LocalDate.now(ZoneOffset.ofTotalSeconds(tzOffsetMinutes * 60))
        val scan = scanService.analyzePhoto(actor, photo, today, tzOffsetMinutes)
        diaryService.addEntry(
            actor,
            DiaryService.CreateDiaryEntryRequest(MealType.lunch, scanId = scan.scanId),
            today,
        )
        diaryService.addWorkout(
            actor,
            DiaryService.CreateWorkoutRequest(name = "Бег", kcal = 300),
            today,
        )
        val day = diaryService.getDay(actor, today, tzOffsetMinutes)
        assertEquals(100, day.totalKcal)
        assertEquals(300, day.totalBurnedKcal)
        assertEquals(-200, day.netKcal)
        assertEquals(1, day.workouts.size)
    }
}
