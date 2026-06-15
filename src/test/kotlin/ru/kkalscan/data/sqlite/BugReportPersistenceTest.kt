package ru.kkalscan.data.sqlite

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import ru.kkalscan.data.DatabaseFactory
import ru.kkalscan.data.memory.InMemoryDeviceRepository
import ru.kkalscan.data.memory.InMemoryUserRepository
import ru.kkalscan.domain.model.Actor
import ru.kkalscan.domain.service.BugReportServiceImpl
import ru.kkalscan.domain.service.SubscriptionServiceImpl
import ru.kkalscan.integrations.LoggingBugReportMailer
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BugReportPersistenceTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `bug report stores two screenshots in sqlite`() = runTest {
        val dbPath = tempDir.resolve("bug-report-test.db")
        val dataSource = DatabaseFactory.init("jdbc:sqlite:${dbPath}")
        val repo = SqliteBugReportRepository(dataSource)
        val devices = InMemoryDeviceRepository()
        val subscriptionService = SubscriptionServiceImpl(devices, InMemoryUserRepository())
        val mailer = LoggingBugReportMailer()
        val service = BugReportServiceImpl(repo, subscriptionService, mailer)
        val deviceId = UUID.randomUUID()
        devices.getOrCreate(deviceId)
        val actor = Actor(deviceId, null, false, false, emptyList())
        val shot1 = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x01, 0x11)
        val shot2 = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x02, 0x22)

        val result = service.submitBugReport(
            actor = actor,
            email = "tester@example.com",
            description = "Тестовый баг-репорт с двумя скриншотами",
            screenshots = listOf(shot1, shot2),
        )

        assertTrue(result.isPro)
        assertEquals(2, repo.countScreenshots(result.reportId))
        assertEquals(1, mailer.sent.size)
        assertEquals(2, mailer.sent.first().screenshots.size)
    }
}
