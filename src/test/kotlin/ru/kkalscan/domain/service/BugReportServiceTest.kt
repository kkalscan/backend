package ru.kkalscan.domain.service

import kotlinx.coroutines.test.runTest
import ru.kkalscan.data.memory.InMemoryRepositories
import ru.kkalscan.domain.BadRequestException
import ru.kkalscan.domain.BugReportAlreadyUsedException
import ru.kkalscan.domain.model.Actor
import ru.kkalscan.integrations.LoggingBugReportMailer
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BugReportServiceTest {
    private val repos = InMemoryRepositories()
    private val subscriptionService = SubscriptionServiceImpl(repos.devices, repos.users)
    private val mailer = LoggingBugReportMailer()
    private val service = BugReportServiceImpl(repos.bugReports, subscriptionService, mailer)
    private val deviceId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val actor = Actor(
        deviceId = deviceId,
        userId = null,
        isPro = false,
        accountLinked = false,
        linkedProviders = emptyList(),
    )

    @Test
    fun `submit grants pro for first report`() = runTest {
        val result = service.submitBugReport(
            actor = actor,
            email = "test@example.com",
            description = "Кнопка скана не открывает камеру на Android 14",
            screenshots = listOf("fake-image".toByteArray()),
        )

        assertTrue(result.isPro)
        assertTrue(result.proUntil != null)
        val status = subscriptionService.getStatus(actor)
        assertTrue(status.isPro)
    }

    @Test
    fun `second report from same device is rejected`() = runTest {
        service.submitBugReport(
            actor = actor,
            email = "test@example.com",
            description = "Первый баг-репорт с достаточным описанием",
            screenshots = emptyList(),
        )

        assertFailsWith<BugReportAlreadyUsedException> {
            service.submitBugReport(
                actor = actor,
                email = "other@example.com",
                description = "Второй баг-репорт с достаточным описанием",
                screenshots = emptyList(),
            )
        }
    }

    @Test
    fun `invalid email is rejected`() = runTest {
        assertFailsWith<BadRequestException> {
            service.submitBugReport(
                actor = actor,
                email = "not-an-email",
                description = "Описание бага достаточной длины",
                screenshots = emptyList(),
            )
        }
    }

    @Test
    fun `short description is rejected`() = runTest {
        assertFailsWith<BadRequestException> {
            service.submitBugReport(
                actor = actor,
                email = "test@example.com",
                description = "коротко",
                screenshots = emptyList(),
            )
        }
    }
}
