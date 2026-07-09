package ru.kkalscan.domain.service

import ru.kkalscan.AppConfig
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.roundToInt

object ActivityEmulatorTimeProration {
    fun prorateForDaylight(
        fullDayKcal: Int,
        timezoneOffsetMinutes: Int,
        now: Instant,
        daylightStartHour: Int = AppConfig.ACTIVITY_EMULATOR_DAYLIGHT_START_HOUR,
        daylightEndHour: Int = AppConfig.ACTIVITY_EMULATOR_DAYLIGHT_END_HOUR,
    ): Int {
        if (fullDayKcal <= 0) return 0
        val zone = ZoneOffset.ofTotalSeconds(timezoneOffsetMinutes * 60)
        val localTime = now.atZone(zone).toLocalTime()
        val start = LocalTime.of(daylightStartHour, 0)
        val end = LocalTime.of(daylightEndHour, 0)
        val totalMinutes = Duration.between(start, end).toMinutes()
        if (totalMinutes <= 0) return fullDayKcal

        val elapsedMinutes = when {
            localTime.isBefore(start) -> 0L
            !localTime.isBefore(end) -> totalMinutes
            else -> Duration.between(start, localTime).toMinutes()
        }
        val fraction = elapsedMinutes.toDouble() / totalMinutes.toDouble()
        return (fullDayKcal * fraction).roundToInt().coerceIn(0, fullDayKcal)
    }
}
