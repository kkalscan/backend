package ru.kkalscan.data.sqlite

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.io.TempDir
import ru.kkalscan.TestFixtures
import ru.kkalscan.domain.port.PromoCode
import ru.kkalscan.domain.service.PromoService
import java.nio.file.Path
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PromoPersistenceTest {
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
    fun `seeded Lida promo survives restart and is case-insensitive`() {
        val dataSource = freshDatabase("promo-seed.db")
        val first = SqlitePromoCodeRepository(dataSource)
        assertEquals(50, first.findActive("lida")?.discountPercent)
        assertEquals("Lida", first.findActive("LIDA")?.code)

        val restarted = SqlitePromoCodeRepository(dataSource)
        assertEquals(50, restarted.findActive("Lida")?.discountPercent)
        assertNull(restarted.findActive("nope"))
    }

    @Test
    fun `device promo binding survives restart`() = runTest {
        val dataSource = freshDatabase("promo-bind.db")
        val deviceId = TestFixtures.deviceId
        SqliteDeviceRepository(dataSource).getOrCreate(deviceId)

        val codes = SqlitePromoCodeRepository(dataSource)
        val bindings = SqliteDevicePromoBindingRepository(dataSource)
        bindings.bind(deviceId, "Lida")

        val restarted = SqliteDevicePromoBindingRepository(dataSource)
        assertEquals("Lida", restarted.getBoundCode(deviceId))

        val service = PromoService(codes, restarted)
        val bound = service.getBoundPromo(deviceId)
        assertEquals("Lida", bound?.promoCode)
        assertEquals(50, bound?.discountPercent)
    }

    @Test
    fun `upsert inactive promo hides it from findActive`() {
        val dataSource = freshDatabase("promo-inactive.db")
        val repo = SqlitePromoCodeRepository(dataSource)
        repo.upsert(PromoCode(code = "Temp", discountPercent = 10, active = true))
        assertEquals(10, repo.findActive("temp")?.discountPercent)

        repo.upsert(PromoCode(code = "Temp", discountPercent = 10, active = false))
        assertNull(repo.findActive("Temp"))
    }

    @Test
    fun `binding is per device`() = runTest {
        val dataSource = freshDatabase("promo-iso.db")
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val devices = SqliteDeviceRepository(dataSource)
        devices.getOrCreate(a)
        devices.getOrCreate(b)

        val bindings = SqliteDevicePromoBindingRepository(dataSource)
        bindings.bind(a, "Lida")
        assertEquals("Lida", bindings.getBoundCode(a))
        assertNull(bindings.getBoundCode(b))
    }
}
