package ru.kkalscan.data.sqlite

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.io.TempDir
import ru.kkalscan.TestFixtures
import java.nio.file.Path
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScanQuotaPersistenceTest {
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
    fun `quota survives restart after increment and bonus`() = runTest {
        val dataSource = freshDatabase("quota-restart.db")
        val deviceId = TestFixtures.deviceId
        val date = LocalDate.of(2026, 7, 24)

        SqliteDeviceRepository(dataSource).getOrCreate(deviceId)

        val repo = SqliteScanQuotaRepository(dataSource)
        val created = repo.getOrCreate(deviceId, date)
        assertEquals(0, created.scansUsed)
        assertFalse(created.bonusGranted)

        repo.incrementUsed(deviceId, date)
        repo.incrementUsed(deviceId, date)
        repo.grantBonus(deviceId, date)

        val restarted = SqliteScanQuotaRepository(dataSource)
        val loaded = restarted.getOrCreate(deviceId, date)
        assertEquals(2, loaded.scansUsed)
        assertTrue(loaded.bonusGranted)
        assertEquals(2, loaded.bonusScans)
    }

    @Test
    fun `getOrCreate is idempotent for same device and date`() = runTest {
        val dataSource = freshDatabase("quota-idempotent.db")
        val deviceId = TestFixtures.deviceId
        val date = LocalDate.of(2026, 7, 24)
        SqliteDeviceRepository(dataSource).getOrCreate(deviceId)

        val repo = SqliteScanQuotaRepository(dataSource)
        repo.incrementUsed(deviceId, date)
        val again = repo.getOrCreate(deviceId, date)
        assertEquals(1, again.scansUsed)
    }
}
