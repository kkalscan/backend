package ru.kkalscan.domain.service

import ru.kkalscan.AppConfig
import ru.kkalscan.domain.port.ActivityEmulatorMode
import ru.kkalscan.domain.port.ActivityEmulatorResponse
import ru.kkalscan.domain.port.ActivityEmulatorService
import ru.kkalscan.domain.port.DiaryRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt

class ActivityEmulatorServiceImpl(
    private val diaryRepository: DiaryRepository,
    private val clock: () -> Instant = { Instant.now() },
) : ActivityEmulatorService {

    override suspend fun getEmulator(
        deviceId: UUID,
        today: LocalDate,
        timezoneOffsetMinutes: Int,
    ): ActivityEmulatorResponse {
        val lookback = AppConfig.ACTIVITY_EMULATOR_LOOKBACK_DAYS
        val from = today.minusDays(lookback.toLong() - 1)
        val byDay = diaryRepository.consumedKcalByDay(deviceId, from, today, timezoneOffsetMinutes)
        val daysWithFood = byDay.filterValues { it > 0 }
        val now = clock()
        if (daysWithFood.isEmpty()) {
            return populationDefault(lookback, timezoneOffsetMinutes, now)
        }
        val avg = daysWithFood.values.sum() / daysWithFood.size
        val fullDayActive = (avg - AppConfig.ACTIVITY_EMULATOR_BMR_DEFAULT)
            .coerceIn(MIN_ACTIVE_KCAL, MAX_ACTIVE_KCAL)
        val active = prorate(fullDayActive, timezoneOffsetMinutes, now)
        return ActivityEmulatorResponse(
            mode = ActivityEmulatorMode.diary_based,
            estimatedActiveKcal = active,
            estimatedSteps = stepsFromActiveKcal(active),
            avgConsumedKcalPerDay = avg,
            diaryDaysWithEntries = daysWithFood.size,
            lookbackDays = lookback,
        )
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
