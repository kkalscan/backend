package ru.kkalscan.data.sqlite

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.io.TempDir
import ru.kkalscan.TestFixtures
import ru.kkalscan.data.memory.InMemoryDiaryRepository
import ru.kkalscan.data.memory.InMemoryRepositories
import ru.kkalscan.domain.model.DishDto
import ru.kkalscan.domain.model.MealType
import ru.kkalscan.domain.port.DiaryService
import ru.kkalscan.domain.port.WorkoutRecord
import ru.kkalscan.domain.service.DiaryServiceImpl
import ru.kkalscan.domain.service.QuotaServiceImpl
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkoutPersistenceTest {
    @TempDir
    lateinit var tempDir: Path

    private fun freshDatabase(fileName: String): DataSource {
        val dbPath = tempDir.resolve(fileName)
        val ds = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:sqlite:${dbPath}"
                maximumPoolSize = 2
            },
        )
        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        return ds
    }

    @Test
    fun `workout survives sqlite round trip and affects diary balance`() = runTest {
        val dataSource = freshDatabase("workout-test.db")
        val diaryRepo = InMemoryDiaryRepository()
        val workoutRepo = SqliteWorkoutRepository(dataSource)
        val repos = InMemoryRepositories()
        val quotaService = QuotaServiceImpl(repos.quotas, repos.devices, repos.users)
        val diaryService = DiaryServiceImpl(
            diaryRepo,
            workoutRepo,
            quotaService,
            repos.scanSessions,
        )
        val actor = TestFixtures.guestActor()
        val today = LocalDate.of(2026, 7, 7)

        diaryService.addEntry(
            actor,
            DiaryService.CreateDiaryEntryRequest(
                mealType = MealType.lunch,
                dishes = listOf(
                    DishDto(
                        name = "Суп",
                        grams = 300,
                        kcal = 400,
                        protein = 10.0,
                        fat = 5.0,
                        carbs = 40.0,
                    ),
                ),
            ),
            today,
        )
        diaryService.addWorkout(
            actor,
            DiaryService.CreateWorkoutRequest(name = "Бег", kcal = 150),
            today,
        )

        val restartedWorkoutRepo = SqliteWorkoutRepository(dataSource)
        val day = DiaryServiceImpl(
            diaryRepo,
            restartedWorkoutRepo,
            quotaService,
            repos.scanSessions,
        ).getDay(actor, today, timezoneOffsetMinutes = 180)

        assertEquals(400, day.totalKcal)
        assertEquals(150, day.totalBurnedKcal)
        assertEquals(250, day.netKcal)
        assertEquals(1, day.workouts.size)
        assertEquals("Бег", day.workouts.single().name)
    }

    @Test
    fun `delete workout removes row from sqlite`() = runTest {
        val repo = SqliteWorkoutRepository(freshDatabase("workout-delete-test.db"))
        val workoutId = UUID.randomUUID()
        val deviceId = TestFixtures.deviceId

        repo.insert(
            WorkoutRecord(
                id = workoutId,
                deviceId = deviceId,
                userId = null,
                name = "Йога",
                kcal = 80,
                createdAt = Instant.parse("2026-07-07T10:00:00Z"),
            ),
        )
        assertEquals(1, repo.findByDevice(deviceId, LocalDate.of(2026, 7, 7), 180).size)

        repo.delete(workoutId)
        assertNull(repo.findById(workoutId))
        assertEquals(0, repo.findByDevice(deviceId, LocalDate.of(2026, 7, 7), 180).size)
    }

    @Test
    fun `merge assigns user_id on device workouts`() = runTest {
        val repo = SqliteWorkoutRepository(freshDatabase("workout-merge-test.db"))
        val deviceId = TestFixtures.deviceId
        val userId = UUID.randomUUID()

        repo.insert(
            WorkoutRecord(
                id = UUID.randomUUID(),
                deviceId = deviceId,
                userId = null,
                name = "Плавание",
                kcal = 200,
                createdAt = Instant.parse("2026-07-07T12:00:00Z"),
            ),
        )
        repo.updateUserIdForDevice(deviceId, userId)

        val byUser = repo.findByUser(userId, LocalDate.of(2026, 7, 7), 180)
        assertEquals(1, byUser.size)
        assertEquals(userId, byUser.single().userId)
    }
}
