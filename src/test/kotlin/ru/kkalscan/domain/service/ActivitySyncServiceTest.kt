package ru.kkalscan.domain.service

import kotlinx.coroutines.test.runTest
import ru.kkalscan.TestFixtures
import ru.kkalscan.data.memory.InMemoryRepositories
import ru.kkalscan.domain.port.ActivitySourceKind
import ru.kkalscan.domain.port.DiaryService
import ru.kkalscan.integrations.StubVisionClient
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ActivitySyncServiceTest {
    private val repos = InMemoryRepositories()
    private val quotaService = QuotaServiceImpl(repos.quotas, repos.devices, repos.users)
    private val diaryService = DiaryServiceImpl(
        repos.diary,
        repos.workouts,
        repos.dailyActivity,
        quotaService,
        repos.scanSessions,
        StubVisionClient(),
        repos.visionBudget,
    )
    private val actor = TestFixtures.guestActor()
    private val date = LocalDate.of(2026, 7, 9)

    @Test
    fun `activity sync persists steps and sums with workouts in total burned`() = runTest {
        diaryService.addWorkout(actor, DiaryService.CreateWorkoutRequest(name = "Бег", kcal = 180), date)

        val synced = diaryService.syncActivity(
            actor,
            DiaryService.SyncActivityRequest(
                steps = 8750,
                kcal = 350,
                source = ActivitySourceKind.DeviceSensor,
            ),
            date,
            timezoneOffsetMinutes = 180,
        )

        assertEquals(350, synced.activityKcal)
        assertEquals(8750, synced.activitySteps)
        assertEquals(530, synced.totalBurnedKcal)
        assertEquals(ActivitySourceKind.DeviceSensor, synced.activitySource)
    }

    @Test
    fun `activity sync monotonic increase only`() = runTest {
        diaryService.syncActivity(
            actor,
            DiaryService.SyncActivityRequest(5000, 200, ActivitySourceKind.DeviceSensor),
            date,
            180,
        )
        val updated = diaryService.syncActivity(
            actor,
            DiaryService.SyncActivityRequest(4000, 150, ActivitySourceKind.DeviceSensor),
            date,
            180,
        )

        assertEquals(200, updated.activityKcal)
        assertEquals(5000, updated.activitySteps)
    }
}
