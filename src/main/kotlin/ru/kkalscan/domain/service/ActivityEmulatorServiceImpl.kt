package ru.kkalscan.domain.service

import ru.kkalscan.AppConfig
import ru.kkalscan.domain.port.ActivityEmulatorMode
import ru.kkalscan.domain.port.ActivityEmulatorResponse
import ru.kkalscan.domain.port.ActivityEmulatorService
import ru.kkalscan.domain.port.DailyActivityRepository
import ru.kkalscan.domain.port.WorkoutRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt

class ActivityEmulatorServiceImpl(
    private val workoutRepository: WorkoutRepository,
    private val dailyActivityRepository: DailyActivityRepository,
    private val clock: () -> Instant = { Instant.now() },
) : ActivityEmulatorService {

    override suspend fun getEmulator(
        deviceId: UUID,
        today: LocalDate,
        timezoneOffsetMinutes: Int,
    ): ActivityEmulatorResponse {
        val lookback = AppConfig.ACTIVITY_EMULATOR_LOOKBACK_DAYS
        val from = today.minusDays(lookback.toLong() - 1)
        val byDay = burnedKcalByDay(deviceId, from, today, timezoneOffsetMinutes)
        val daysWithBurn = byDay.filterValues { it > 0 }
        val now = clock()
        if (daysWithBurn.isEmpty()) {
            return populationDefault(lookback, timezoneOffsetMinutes, now)
        }
        val avgBurn = daysWithBurn.values.sum() / daysWithBurn.size
        val fullDayActive = avgBurn.coerceIn(MIN_ACTIVE_KCAL, MAX_ACTIVE_KCAL)
        val active = prorate(fullDayActive, timezoneOffsetMinutes, now)
        return ActivityEmulatorResponse(
            mode = ActivityEmulatorMode.diary_based,
            estimatedActiveKcal = active,
            estimatedSteps = stepsFromActiveKcal(active),
            avgConsumedKcalPerDay = avgBurn,
            diaryDaysWithEntries = daysWithBurn.size,
            lookbackDays = lookback,
        )
    }

    private suspend fun burnedKcalByDay(
        deviceId: UUID,
        from: LocalDate,
        to: LocalDate,
        timezoneOffsetMinutes: Int,
    ): Map<LocalDate, Int> {
        val result = linkedMapOf<LocalDate, Int>()
        var date = from
        while (!date.isAfter(to)) {
            val workoutBurned = workoutRepository.findByDevice(deviceId, date, timezoneOffsetMinutes).sumOf { it.kcal }
            val activityBurned = dailyActivityRepository.findByDevice(deviceId, date)?.kcal ?: 0
            val burned = workoutBurned + activityBurned
            if (burned > 0) {
                result[date] = burned
            }
            date = date.plusDays(1)
        }
        return result
    }

    private fun populationDefault(
        lookbackDays: Int,
        timezoneOffsetMinutes: Int,
        now: Instant,
    ): ActivityEmulatorResponse {
        val fullDay = AppConfig.ACTIVITY_EMULATOR_FULL_DAYLIGHT_ACTIVE_KCAL
        val active = prorate(fullDay, timezoneOffsetMinutes, now)
        return ActivityEmulatorResponse(
            mode = ActivityEmulatorMode.population_default,
            estimatedActiveKcal = active,
            estimatedSteps = stepsFromActiveKcal(active),
            avgConsumedKcalPerDay = null,
            diaryDaysWithEntries = 0,
            lookbackDays = lookbackDays,
        )
    }

    private fun prorate(fullDayKcal: Int, timezoneOffsetMinutes: Int, now: Instant): Int =
        ActivityEmulatorTimeProration.prorateForDaylight(fullDayKcal, timezoneOffsetMinutes, now)

    private fun stepsFromActiveKcal(activeKcal: Int): Int =
        if (activeKcal <= 0) 0 else (activeKcal / AppConfig.ACTIVITY_EMULATOR_KCAL_PER_STEP).roundToInt()

    private companion object {
        const val MIN_ACTIVE_KCAL = 100
        const val MAX_ACTIVE_KCAL = 800
    }
}
