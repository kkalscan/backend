package ru.kkalscan.domain.service

import ru.kkalscan.AppConfig
import ru.kkalscan.domain.port.ActivityEmulatorMode
import ru.kkalscan.domain.port.ActivityEmulatorResponse
import ru.kkalscan.domain.port.ActivityEmulatorService
import ru.kkalscan.domain.port.DiaryRepository
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt

class ActivityEmulatorServiceImpl(
    private val diaryRepository: DiaryRepository,
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
        if (daysWithFood.isEmpty()) {
            return populationDefault(lookback)
        }
        val avg = daysWithFood.values.sum() / daysWithFood.size
        val active = (avg - AppConfig.ACTIVITY_EMULATOR_BMR_DEFAULT)
            .coerceIn(MIN_ACTIVE_KCAL, MAX_ACTIVE_KCAL)
        return ActivityEmulatorResponse(
            mode = ActivityEmulatorMode.diary_based,
            estimatedActiveKcal = active,
            estimatedSteps = stepsFromActiveKcal(active),
            avgConsumedKcalPerDay = avg,
            diaryDaysWithEntries = daysWithFood.size,
            lookbackDays = lookback,
        )
    }

    private fun populationDefault(lookbackDays: Int): ActivityEmulatorResponse {
        val active = AppConfig.ACTIVITY_EMULATOR_DEFAULT_ACTIVE_KCAL
        return ActivityEmulatorResponse(
            mode = ActivityEmulatorMode.population_default,
            estimatedActiveKcal = active,
            estimatedSteps = stepsFromActiveKcal(active),
            avgConsumedKcalPerDay = null,
            diaryDaysWithEntries = 0,
            lookbackDays = lookbackDays,
        )
    }

    private fun stepsFromActiveKcal(activeKcal: Int): Int =
        (activeKcal / AppConfig.ACTIVITY_EMULATOR_KCAL_PER_STEP).roundToInt()

    private companion object {
        const val MIN_ACTIVE_KCAL = 100
        const val MAX_ACTIVE_KCAL = 800
    }
}
