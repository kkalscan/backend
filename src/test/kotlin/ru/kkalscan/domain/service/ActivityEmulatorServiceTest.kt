package ru.kkalscan.domain.service

import kotlinx.coroutines.test.runTest
import ru.kkalscan.data.memory.InMemoryDiaryRepository
import ru.kkalscan.domain.model.MealType
import ru.kkalscan.domain.port.ActivityEmulatorMode
import ru.kkalscan.domain.port.DiaryEntryRecord
import ru.kkalscan.TestFixtures
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActivityEmulatorServiceTest {
    private val deviceId = TestFixtures.deviceId
    private val today = LocalDate.of(2026, 7, 9)
    private val tzOffset = 180
    private val zone = ZoneOffset.ofTotalSeconds(tzOffset * 60)

    @Test
    fun populationDefaultAtDaylightStartIsZero() = runTest {
        val service = ActivityEmulatorServiceImpl(InMemoryDiaryRepository()) {
            instant(2026, 7, 9, 7, 0)
        }
        val result = service.getEmulator(deviceId, today, tzOffset)
        assertEquals(ActivityEmulatorMode.population_default, result.mode)
        assertEquals(0, result.estimatedActiveKcal)
        assertEquals(0, result.estimatedSteps)
    }

    @Test
    fun populationDefaultAtMidDayIsHalfOfFullDaylight() = runTest {
        val service = ActivityEmulatorServiceImpl(InMemoryDiaryRepository()) {
            instant(2026, 7, 9, 15, 0)
        }
        val result = service.getEmulator(deviceId, today, tzOffset)
        assertEquals(ActivityEmulatorMode.population_default, result.mode)
        assertEquals(750, result.estimatedActiveKcal)
        assertEquals(18750, result.estimatedSteps)
        assertNull(result.avgConsumedKcalPerDay)
    }

    @Test
    fun populationDefaultAfterDaylightEndIsFullDay() = runTest {
        val service = ActivityEmulatorServiceImpl(InMemoryDiaryRepository()) {
            instant(2026, 7, 9, 23, 30)
        }
        val result = service.getEmulator(deviceId, today, tzOffset)
        assertEquals(1500, result.estimatedActiveKcal)
        assertEquals(37500, result.estimatedSteps)
    }

    @Test
    fun diaryBasedIsProratedByTimeOfDay() = runTest {
        val repo = InMemoryDiaryRepository()
        val service = ActivityEmulatorServiceImpl(repo) { instant(2026, 7, 9, 15, 0) }
        listOf(
            LocalDate.of(2026, 7, 7),
            LocalDate.of(2026, 7, 8),
            LocalDate.of(2026, 7, 9),
        ).forEach { date ->
            repo.insertEntry(
                entry = diaryEntry(date.atStartOfDay().toInstant(zone), 2000),
                dishes = emptyList(),
            )
        }
        val result = service.getEmulator(deviceId, today, tzOffset)
        assertEquals(ActivityEmulatorMode.diary_based, result.mode)
        assertEquals(2000, result.avgConsumedKcalPerDay)
        assertEquals(250, result.estimatedActiveKcal)
        assertEquals(6250, result.estimatedSteps)
        assertEquals(3, result.diaryDaysWithEntries)
    }

    private fun instant(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant =
        LocalDateTime.of(year, month, day, hour, minute).toInstant(zone)

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
