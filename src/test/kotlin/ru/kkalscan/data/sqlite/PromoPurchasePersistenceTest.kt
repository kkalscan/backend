package ru.kkalscan.data.sqlite

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.io.TempDir
import ru.kkalscan.domain.port.PromoPurchaseRecord
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PromoPurchasePersistenceTest {
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
    fun `promo purchase survives sqlite round trip`() = runTest {
        val dataSource = freshDatabase("promo-purchases.db")
        val repo = SqlitePromoPurchaseRepository(dataSource)
        val paidAt = Instant.parse("2026-07-15T12:00:00Z")
        val record = PromoPurchaseRecord(
            id = UUID.randomUUID(),
            paymentId = UUID.randomUUID(),
            deviceId = UUID.randomUUID(),
            tariff = "pro_monthly_199",
            amountKopecks = 10_000,
            listAmountKopecks = 20_000,
            promoCode = "Lida",
            discountPercent = 50,
            status = "paid",
            paidAt = paidAt,
        )

        repo.record(record)

        val loaded = repo.findById(record.id)
        assertEquals(record.paymentId, loaded?.paymentId)
        assertEquals(record.deviceId, loaded?.deviceId)
        assertEquals("Lida", loaded?.promoCode)
        assertEquals(50, loaded?.discountPercent)
        assertEquals(10_000, loaded?.amountKopecks)
        assertEquals(20_000, loaded?.listAmountKopecks)
        assertEquals("paid", loaded?.status)
        assertEquals(paidAt, loaded?.paidAt)
    }

    @Test
    fun `purchase without promo stores null promo code`() = runTest {
        val dataSource = freshDatabase("promo-purchases-null.db")
        val repo = SqlitePromoPurchaseRepository(dataSource)
        val record = PromoPurchaseRecord(
            id = UUID.randomUUID(),
            paymentId = UUID.randomUUID(),
            deviceId = UUID.randomUUID(),
            tariff = "pro_monthly_199",
            amountKopecks = 20_000,
            listAmountKopecks = 20_000,
            promoCode = null,
            discountPercent = 0,
            status = "free_promo",
            paidAt = Instant.parse("2026-07-15T12:00:00Z"),
        )

        repo.record(record)

        assertNull(repo.findById(record.id)?.promoCode)
    }
}
