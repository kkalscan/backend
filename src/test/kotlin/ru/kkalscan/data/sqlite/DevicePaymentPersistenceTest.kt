package ru.kkalscan.data.sqlite

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.io.TempDir
import ru.kkalscan.domain.port.PaymentRecord
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DevicePaymentPersistenceTest {
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
    fun `device pro_until survives sqlite round trip`() = runTest {
        val dataSource = freshDatabase("devices.db")
        val repo = SqliteDeviceRepository(dataSource)
        val deviceId = UUID.randomUUID()
        val until = Instant.parse("2026-08-23T09:37:50Z")

        repo.getOrCreate(deviceId)
        repo.setProUntil(deviceId, until)

        val loaded = SqliteDeviceRepository(dataSource).findById(deviceId)
        assertNotNull(loaded)
        assertEquals(until, loaded.proUntil)
        assertNull(loaded.userId)
    }

    @Test
    fun `payment create and markPaid survive sqlite round trip`() = runTest {
        val dataSource = freshDatabase("payments.db")
        val devices = SqliteDeviceRepository(dataSource)
        val payments = SqlitePaymentRepository(dataSource)
        val deviceId = UUID.randomUUID()
        val paymentId = UUID.randomUUID()
        devices.getOrCreate(deviceId)

        payments.create(
            PaymentRecord(
                id = paymentId,
                deviceId = deviceId,
                userId = null,
                tochkaPaymentId = "c39be54b-7e06-4732-b310-468e17aabb0c",
                amountKopecks = 20_000,
                tariff = "pro_monthly_199",
                status = "pending",
                paidAt = null,
                promoCode = null,
                discountPercent = 0,
                listAmountKopecks = 20_000,
            ),
        )

        val pending = payments.findById(paymentId)
        assertEquals("pending", pending?.status)
        assertEquals(20_000, pending?.listAmountKopecks)

        val paidAt = Instant.parse("2026-07-24T06:37:50Z")
        payments.markPaid(paymentId, "c39be54b-7e06-4732-b310-468e17aabb0c", paidAt)

        val byTochka = SqlitePaymentRepository(dataSource)
            .findByTochkaId("c39be54b-7e06-4732-b310-468e17aabb0c")
        assertNotNull(byTochka)
        assertEquals("paid", byTochka.status)
        assertEquals(paidAt, byTochka.paidAt)
        assertEquals(deviceId, byTochka.deviceId)
    }

    @Test
    fun `findPendingByDevice returns only pending for device`() = runTest {
        val dataSource = freshDatabase("payments-pending.db")
        val devices = SqliteDeviceRepository(dataSource)
        val payments = SqlitePaymentRepository(dataSource)
        val deviceId = UUID.randomUUID()
        devices.getOrCreate(deviceId)

        val pendingId = UUID.randomUUID()
        payments.create(
            PaymentRecord(
                id = pendingId,
                deviceId = deviceId,
                userId = null,
                tochkaPaymentId = "op-pending",
                amountKopecks = 20_000,
                tariff = "pro_monthly_199",
                status = "pending",
                paidAt = null,
            ),
        )
        payments.create(
            PaymentRecord(
                id = UUID.randomUUID(),
                deviceId = deviceId,
                userId = null,
                tochkaPaymentId = "op-paid",
                amountKopecks = 20_000,
                tariff = "pro_monthly_199",
                status = "paid",
                paidAt = Instant.parse("2026-07-24T06:37:50Z"),
            ),
        )

        val pending = payments.findPendingByDevice(deviceId)
        assertEquals(1, pending.size)
        assertEquals(pendingId, pending.first().id)
        assertTrue(pending.all { it.status == "pending" })

        val allPending = payments.findAllPending()
        assertEquals(1, allPending.size)
        assertEquals(pendingId, allPending.first().id)
    }
}
