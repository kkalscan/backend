package ru.kkalscan.domain.service

import kotlinx.coroutines.test.runTest
import ru.kkalscan.data.memory.InMemoryDiaryRepository
import ru.kkalscan.domain.model.MealType
import ru.kkalscan.domain.port.ActivityEmulatorMode
import ru.kkalscan.domain.port.DiaryEntryRecord
import ru.kkalscan.TestFixtures
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActivityEmulatorServiceTest {
    private val deviceId = TestFixtures.deviceId
    private val today = LocalDate.of(2026, 7, 9)
    private val tzOffset = 180

    @Test
    fun populationDefaultWhenNoDiary() = runTest {
        val service = ActivityEmulatorServiceImpl(InMemoryDiaryRepository())
        val result = service.getEmulator(deviceId, today, tzOffset)
        assertEquals(ActivityEmulatorMode.population_default, result.mode)
        assertEquals(400, result.estimatedActiveKcal)
        assertEquals(10000, result.estimatedSteps)
        assertNull(result.avgConsumedKcalPerDay)
        assertEquals(0, result.diaryDaysWithEntries)
    }

    @Test
    fun diaryBasedFromAverageConsumption() = runTest {
        val repo = InMemoryDiaryRepository()
        val service = ActivityEmulatorServiceImpl(repo)
        val offset = ZoneOffset.ofTotalSeconds(tzOffset * 60)
        listOf(
            LocalDate.of(2026, 7, 7),
            LocalDate.of(2026, 7, 8),
            LocalDate.of(2026, 7, 9),
        ).forEach { date ->
            repo.insertEntry(
                entry = diaryEntry(date.atStartOfDay().toInstant(offset), 2000),
                dishes = emptyList(),
            )
        }
        val result = service.getEmulator(deviceId, today, tzOffset)
        assertEquals(ActivityEmulatorMode.diary_based, result.mode)
        assertEquals(2000, result.avgConsumedKcalPerDay)
        assertEquals(500, result.estimatedActiveKcal)
        assertEquals(12500, result.estimatedSteps)
        assertEquals(3, result.diaryDaysWithEntries)
    }

    private fun diaryEntry(at: Instant, kcal: Int) = DiaryEntryRecord(
        id = UUID.randomUUID(),
        deviceId = deviceId,
        userId = null,
        mealType = MealType.lunch,
        scanSessionId = null,
        totalKcal = kcal,
        createdAt = at,
        dishes = emptyList(),
    )
}
