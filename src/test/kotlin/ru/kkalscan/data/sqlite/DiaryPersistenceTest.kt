package ru.kkalscan.data.sqlite

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.io.TempDir
import ru.kkalscan.TestFixtures
import ru.kkalscan.data.memory.InMemoryRepositories
import ru.kkalscan.domain.model.DishDto
import ru.kkalscan.domain.model.MealType
import ru.kkalscan.domain.port.DiaryEntryRecord
import ru.kkalscan.domain.port.DiaryService
import ru.kkalscan.domain.service.AccountMergeServiceImpl
import ru.kkalscan.domain.service.DiaryServiceImpl
import ru.kkalscan.domain.service.QuotaServiceImpl
import ru.kkalscan.integrations.StubVisionClient
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiaryPersistenceTest {
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

    private fun sampleDishes() = listOf(
        DishDto(
            name = "Суп",
            grams = 300,
            kcal = 250,
            protein = 12.0,
            fat = 8.0,
            carbs = 30.0,
            fiber = 2.5,
        ),
        DishDto(
            name = "Хлеб",
            grams = 40,
            kcal = 100,
            protein = 3.0,
            fat = 1.0,
            carbs = 20.0,
            fiber = 1.0,
        ),
    )

    @Test
    fun `diary entry survives sqlite restart with dishes and fiber`() = runTest {
        val dataSource = freshDatabase("diary-restart.db")
        val repo = SqliteDiaryRepository(dataSource)
        val entryId = UUID.randomUUID()
        val deviceId = TestFixtures.deviceId
        val dishes = sampleDishes()

        repo.insertEntry(
            DiaryEntryRecord(
                id = entryId,
                deviceId = deviceId,
                userId = null,
                mealType = MealType.lunch,
                scanSessionId = null,
                totalKcal = dishes.sumOf { it.kcal },
                createdAt = Instant.parse("2026-07-24T10:00:00Z"),
                dishes = emptyList(),
            ),
            dishes,
        )

        val restarted = SqliteDiaryRepository(dataSource)
        val found = restarted.findEntry(entryId)
        assertEquals(entryId, found?.id)
        assertEquals(MealType.lunch, found?.mealType)
        assertEquals(350, found?.totalKcal)
        assertEquals(2, found?.dishes?.size)
        assertEquals(2.5, found?.dishes?.first()?.fiber)
        assertEquals("Хлеб", found?.dishes?.last()?.name)

        val byDay = restarted.findEntriesByDevice(deviceId, LocalDate.of(2026, 7, 24), 180)
        assertEquals(1, byDay.size)
        assertEquals(2, byDay.single().dishes.size)
    }

    @Test
    fun `delete entry removes dishes cascade`() = runTest {
        val dataSource = freshDatabase("diary-delete.db")
        val repo = SqliteDiaryRepository(dataSource)
        val entryId = UUID.randomUUID()
        val dishes = sampleDishes()

        repo.insertEntry(
            DiaryEntryRecord(
                id = entryId,
                deviceId = TestFixtures.deviceId,
                userId = null,
                mealType = MealType.breakfast,
                scanSessionId = null,
                totalKcal = 350,
                createdAt = Instant.parse("2026-07-24T06:00:00Z"),
                dishes = emptyList(),
            ),
            dishes,
        )
        repo.deleteEntry(entryId)

        val restarted = SqliteDiaryRepository(dataSource)
        assertNull(restarted.findEntry(entryId))
        assertEquals(0, restarted.findEntriesByDevice(TestFixtures.deviceId, LocalDate.of(2026, 7, 24), 180).size)
    }

    @Test
    fun `timezone day bounds match local calendar day`() = runTest {
        val repo = SqliteDiaryRepository(freshDatabase("diary-tz.db"))
        val deviceId = TestFixtures.deviceId
        // 2026-07-23 22:30 MSK = 19:30 UTC → still 23rd in MSK (+180)
        repo.insertEntry(
            DiaryEntryRecord(
                id = UUID.randomUUID(),
                deviceId = deviceId,
                userId = null,
                mealType = MealType.dinner,
                scanSessionId = null,
                totalKcal = 500,
                createdAt = Instant.parse("2026-07-23T19:30:00Z"),
                dishes = emptyList(),
            ),
            listOf(
                DishDto("Ужин", 200, 500, 20.0, 15.0, 40.0),
            ),
        )

        assertEquals(1, repo.findEntriesByDevice(deviceId, LocalDate.of(2026, 7, 23), 180).size)
        assertEquals(0, repo.findEntriesByDevice(deviceId, LocalDate.of(2026, 7, 24), 180).size)
    }

    @Test
    fun `consumedKcalByDay aggregates after restart`() = runTest {
        val dataSource = freshDatabase("diary-consumed.db")
        val repo = SqliteDiaryRepository(dataSource)
        val deviceId = TestFixtures.deviceId

        repo.insertEntry(
            DiaryEntryRecord(
                id = UUID.randomUUID(),
                deviceId = deviceId,
                userId = null,
                mealType = MealType.breakfast,
                scanSessionId = null,
                totalKcal = 200,
                createdAt = Instant.parse("2026-07-20T08:00:00Z"),
                dishes = emptyList(),
            ),
            listOf(DishDto("A", 100, 200, 5.0, 5.0, 20.0)),
        )
        repo.insertEntry(
            DiaryEntryRecord(
                id = UUID.randomUUID(),
                deviceId = deviceId,
                userId = null,
                mealType = MealType.lunch,
                scanSessionId = null,
                totalKcal = 300,
                createdAt = Instant.parse("2026-07-21T12:00:00Z"),
                dishes = emptyList(),
            ),
            listOf(DishDto("B", 150, 300, 10.0, 10.0, 30.0)),
        )

        val restarted = SqliteDiaryRepository(dataSource)
        val map = restarted.consumedKcalByDay(
            deviceId,
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 21),
            180,
        )
        assertEquals(200, map[LocalDate.of(2026, 7, 20)])
        assertEquals(300, map[LocalDate.of(2026, 7, 21)])
    }

    @Test
    fun `device isolation`() = runTest {
        val repo = SqliteDiaryRepository(freshDatabase("diary-iso.db"))
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        repo.insertEntry(
            DiaryEntryRecord(
                id = UUID.randomUUID(),
                deviceId = a,
                userId = null,
                mealType = MealType.snack,
                scanSessionId = null,
                totalKcal = 100,
                createdAt = Instant.parse("2026-07-24T10:00:00Z"),
                dishes = emptyList(),
            ),
            listOf(DishDto("A", 50, 100, 1.0, 1.0, 10.0)),
        )
        assertEquals(1, repo.findEntriesByDevice(a, LocalDate.of(2026, 7, 24), 180).size)
        assertEquals(0, repo.findEntriesByDevice(b, LocalDate.of(2026, 7, 24), 180).size)
    }

    @Test
    fun `merge assigns user_id on diary entries`() = runTest {
        val dataSource = freshDatabase("diary-merge.db")
        val diaryRepo = SqliteDiaryRepository(dataSource)
        val workoutRepo = SqliteWorkoutRepository(dataSource)
        val activityRepo = SqliteDailyActivityRepository(dataSource)
        val repos = InMemoryRepositories()
        val deviceId = TestFixtures.deviceId
        val userId = repos.users.create()

        repos.devices.getOrCreate(deviceId)
        diaryRepo.insertEntry(
            DiaryEntryRecord(
                id = UUID.randomUUID(),
                deviceId = deviceId,
                userId = null,
                mealType = MealType.breakfast,
                scanSessionId = null,
                totalKcal = 150,
                createdAt = Instant.parse("2026-07-24T07:00:00Z"),
                dishes = emptyList(),
            ),
            listOf(DishDto("Каша", 200, 150, 5.0, 3.0, 25.0)),
        )

        AccountMergeServiceImpl(
            repos.devices,
            repos.users,
            diaryRepo,
            workoutRepo,
            activityRepo,
        ).mergeDeviceToUser(deviceId, userId)

        val byUser = diaryRepo.findEntriesByUser(userId, LocalDate.of(2026, 7, 24), 180)
        assertEquals(1, byUser.size)
        assertEquals(userId, byUser.single().userId)
    }

    @Test
    fun `diary service getDay survives repo restart`() = runTest {
        val dataSource = freshDatabase("diary-service.db")
        val repos = InMemoryRepositories()
        val quotaService = QuotaServiceImpl(repos.quotas, repos.devices, repos.users)
        val actor = TestFixtures.guestActor()
        val today = LocalDate.of(2026, 7, 24)

        DiaryServiceImpl(
            SqliteDiaryRepository(dataSource),
            SqliteWorkoutRepository(dataSource),
            SqliteDailyActivityRepository(dataSource),
            quotaService,
            repos.scanSessions,
            StubVisionClient(),
            repos.visionBudget,
        ).addEntry(
            actor,
            DiaryService.CreateDiaryEntryRequest(
                mealType = MealType.lunch,
                dishes = sampleDishes(),
            ),
            today,
        )

        val day = DiaryServiceImpl(
            SqliteDiaryRepository(dataSource),
            SqliteWorkoutRepository(dataSource),
            SqliteDailyActivityRepository(dataSource),
            quotaService,
            repos.scanSessions,
            StubVisionClient(),
            repos.visionBudget,
        ).getDay(actor, today, timezoneOffsetMinutes = 180)

        assertEquals(350, day.totalKcal)
        assertEquals(1, day.entries.size)
        assertEquals(2, day.entries.single().dishes.size)
        assertTrue(day.entries.single().dishes.any { it.fiber == 2.5 })
    }

    @Test
    fun `idempotent insert or ignore same entry id`() = runTest {
        val dataSource = freshDatabase("diary-idempotent.db")
        val entryId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
        val deviceId = TestFixtures.deviceId
        val dish = listOf(DishDto("X", 10, 50, 1.0, 1.0, 5.0))

        fun insertOnce(repo: SqliteDiaryRepository) {
            dataSource.connection.use { conn ->
                conn.prepareStatement(
                    """
                    INSERT OR IGNORE INTO diary_entries
                        (id, device_id, user_id, meal_type, scan_session_id, total_kcal, created_at)
                    VALUES (?, ?, NULL, 'breakfast', NULL, 50, '2026-07-24T01:52:00Z')
                    """.trimIndent(),
                ).use { stmt ->
                    stmt.setString(1, entryId.toString())
                    stmt.setString(2, deviceId.toString())
                    stmt.executeUpdate()
                }
                conn.prepareStatement(
                    """
                    INSERT OR IGNORE INTO diary_dishes
                        (id, entry_id, name, grams, kcal, protein, fat, carbs, fiber)
                    VALUES (?, ?, 'X', 10, 50, 1.0, 1.0, 5.0, 0.0)
                    """.trimIndent(),
                ).use { stmt ->
                    stmt.setString(1, "bbbbbbbb-bbbb-cccc-dddd-eeeeeeeeeeee")
                    stmt.setString(2, entryId.toString())
                    stmt.executeUpdate()
                }
            }
        }

        insertOnce(SqliteDiaryRepository(dataSource))
        insertOnce(SqliteDiaryRepository(dataSource))

        val found = SqliteDiaryRepository(dataSource).findEntry(entryId)
        assertEquals(50, found?.totalKcal)
        assertEquals(1, found?.dishes?.size)
    }
}
