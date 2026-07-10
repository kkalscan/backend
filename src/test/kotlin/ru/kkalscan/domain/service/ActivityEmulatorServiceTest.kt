package ru.kkalscan.domain.service

import kotlinx.coroutines.test.runTest
import ru.kkalscan.data.memory.InMemoryRepositories
import ru.kkalscan.domain.port.ActivityEmulatorMode
import ru.kkalscan.domain.port.ActivitySourceKind
import ru.kkalscan.domain.port.DailyActivityRecord
import ru.kkalscan.domain.port.WorkoutRecord
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
        val repos = InMemoryRepositories()
        val service = ActivityEmulatorServiceImpl(repos.workouts, repos.dailyActivity) {
            instant(2026, 7, 9, 7, 0)
        }
        val result = service.getEmulator(deviceId, today, tzOffset)
        assertEquals(ActivityEmulatorMode.population_default, result.mode)
        assertEquals(0, result.estimatedActiveKcal)
        assertEquals(0, result.estimatedSteps)
    }

    @Test
    fun populationDefaultAtMidDayIsHalfOfFullDaylight() = runTest {
        val repos = InMemoryRepositories()
        val service = ActivityEmulatorServiceImpl(repos.workouts, repos.dailyActivity) {
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
        val repos = InMemoryRepositories()
        val service = ActivityEmulatorServiceImpl(repos.workouts, repos.dailyActivity) {
            instant(2026, 7, 9, 23, 30)
        }
        val result = service.getEmulator(deviceId, today, tzOffset)
        assertEquals(1500, result.estimatedActiveKcal)
        assertEquals(37500, result.estimatedSteps)
    }

    @Test
    fun diaryBasedUsesAverageBurnFromWorkoutsAndActivity() = runTest {
        val repos = InMemoryRepositories()
        val service = ActivityEmulatorServiceImpl(repos.workouts, repos.dailyActivity) {
            instant(2026, 7, 9, 15, 0)
        }
        insertWorkout(repos, LocalDate.of(2026, 7, 7), 400)
        insertWorkout(repos, LocalDate.of(2026, 7, 8), 500)
        insertActivity(repos, LocalDate.of(2026, 7, 9), kcal = 600, steps = 15_000)

        val result = service.getEmulator(deviceId, today, tzOffset)
        assertEquals(ActivityEmulatorMode.diary_based, result.mode)
        assertEquals(500, result.avgConsumedKcalPerDay)
        assertEquals(250, result.estimatedActiveKcal)
        assertEquals(6250, result.estimatedSteps)
        assertEquals(3, result.diaryDaysWithEntries)
    }

    @Test
    fun diaryBasedCountsWorkoutOnlyDays() = runTest {
        val repos = InMemoryRepositories()
        val service = ActivityEmulatorServiceImpl(repos.workouts, repos.dailyActivity) {
            instant(2026, 7, 9, 15, 0)
        }
        listOf(
            LocalDate.of(2026, 7, 7),
            LocalDate.of(2026, 7, 8),
            LocalDate.of(2026, 7, 9),
        ).forEach { date -> insertWorkout(repos, date, 400) }

        val result = service.getEmulator(deviceId, today, tzOffset)
        assertEquals(ActivityEmulatorMode.diary_based, result.mode)
        assertEquals(400, result.avgConsumedKcalPerDay)
        assertEquals(200, result.estimatedActiveKcal)
        assertEquals(3, result.diaryDaysWithEntries)
    }

    private suspend fun insertWorkout(repos: InMemoryRepositories, date: LocalDate, kcal: Int) {
        repos.workouts.insert(
            WorkoutRecord(
                id = UUID.randomUUID(),
                deviceId = deviceId,
                userId = null,
                name = "Бег",
                kcal = kcal,
                createdAt = date.atStartOfDay().toInstant(zone),
            ),
        )
    }

    private suspend fun insertActivity(repos: InMemoryRepositories, date: LocalDate, kcal: Int, steps: Int) {
        repos.dailyActivity.upsert(
            DailyActivityRecord(
                deviceId = deviceId,
                userId = null,
                localDate = date,
                steps = steps,
                kcal = kcal,
                source = ActivitySourceKind.DeviceSensor,
                updatedAt = Instant.now(),
            ),
        )
    }

    private fun instant(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant =
        LocalDateTime.of(year, month, day, hour, minute).toInstant(zone)
}
