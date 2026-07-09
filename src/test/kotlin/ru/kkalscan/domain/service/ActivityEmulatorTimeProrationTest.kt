package ru.kkalscan.domain.service

import ru.kkalscan.AppConfig
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class ActivityEmulatorTimeProrationTest {

    @Test
    fun beforeDaylightReturnsZero() {
        val now = instant(2026, 7, 9, 6, 30, offsetMinutes = 180)
        assertEquals(
            0,
            ActivityEmulatorTimeProration.prorateForDaylight(1500, 180, now),
        )
    }

    @Test
    fun midDaylightReturnsLinearFraction() {
        val now = instant(2026, 7, 9, 15, 0, offsetMinutes = 180)
        assertEquals(
            750,
            ActivityEmulatorTimeProration.prorateForDaylight(
                AppConfig.ACTIVITY_EMULATOR_FULL_DAYLIGHT_ACTIVE_KCAL,
                180,
                now,
            ),
        )
    }

    @Test
    fun afterDaylightReturnsFullAmount() {
        val now = instant(2026, 7, 9, 23, 0, offsetMinutes = 180)
        assertEquals(
            1500,
            ActivityEmulatorTimeProration.prorateForDaylight(1500, 180, now),
        )
    }

    private fun instant(year: Int, month: Int, day: Int, hour: Int, minute: Int, offsetMinutes: Int): Instant {
        val zone = ZoneOffset.ofTotalSeconds(offsetMinutes * 60)
        return LocalDateTime.of(year, month, day, hour, minute).toInstant(zone)
    }
}
