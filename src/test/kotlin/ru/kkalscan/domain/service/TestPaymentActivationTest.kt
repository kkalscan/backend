package ru.kkalscan.domain.service

import kotlinx.coroutines.test.runTest
import ru.kkalscan.TestFixtures
import ru.kkalscan.data.memory.InMemoryRepositories
import ru.kkalscan.domain.ForbiddenException
import ru.kkalscan.integrations.LoggingPlainTextMailer
import ru.kkalscan.integrations.StubTochkaClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TestPaymentActivationTest {
    private val repos = InMemoryRepositories()
    private val subscriptionService = SubscriptionServiceImpl(repos.devices, repos.users)
    private val promoService = PromoService(repos.promoCodes, repos.devicePromoBindings)
    private val mailer = LoggingPlainTextMailer()
    private val service = PaymentServiceImpl(
        repos.payments,
        repos.devices,
        subscriptionService,
        StubTochkaClient(),
        mailer,
        promoService,
        testPaymentNotifyTo = "owner@example.com",
        testPaymentSecret = "test-secret",
    )
    private val deviceId = TestFixtures.deviceId

    @Test
    fun `activate test payment sends email and enables pro`() = runTest {
        val result = service.activateTestPayment(deviceId, "test-secret")

        assertTrue(result.isPro)
        assertTrue(result.emailSent)
        assertEquals(SubscriptionServiceImpl.TARIFF, result.tariff)
        assertEquals(1, mailer.sent.size)
        assertEquals("owner@example.com", mailer.sent.single().first)
        assertTrue(mailer.sent.single().second.contains("тестовая оплата"))
        assertTrue(subscriptionService.getStatus(TestFixtures.guestActor()).isPro)
    }

    @Test
    fun `wrong secret rejected`() = runTest {
        assertFailsWith<ForbiddenException> {
            service.activateTestPayment(deviceId, "wrong")
        }
        assertTrue(!subscriptionService.getStatus(TestFixtures.guestActor()).isPro)
        assertTrue(mailer.sent.isEmpty())
    }
}
