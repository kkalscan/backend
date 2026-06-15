package ru.kkalscan.domain.service

import kotlinx.coroutines.test.runTest
import ru.kkalscan.TestFixtures
import ru.kkalscan.data.memory.InMemoryRepositories
import ru.kkalscan.domain.BadRequestException
import ru.kkalscan.integrations.StubTochkaClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PaymentServiceTest {
    private val repos = InMemoryRepositories()
    private val subscriptionService = SubscriptionServiceImpl(repos.devices, repos.users)
    private val service = PaymentServiceImpl(
        repos.payments,
        repos.devices,
        subscriptionService,
        StubTochkaClient(),
    )
    private val deviceId = TestFixtures.deviceId

    @Test
    fun `create payment returns stub url`() = runTest {
        val response = service.createTochkaPayment(deviceId, SubscriptionServiceImpl.TARIFF)

        assertTrue(response.paymentUrl.contains("pay.tochka.example"))
        assertTrue(response.paymentId.toString().isNotBlank())
    }

    @Test
    fun `unknown tariff rejected`() = runTest {
        assertFailsWith<BadRequestException> {
            service.createTochkaPayment(deviceId, "unknown")
        }
    }

    @Test
    fun `paid webhook activates pro`() = runTest {
        val response = service.createTochkaPayment(deviceId, SubscriptionServiceImpl.TARIFF)
        val tochkaId = "tochka_${response.paymentId.toString().take(8)}"

        service.handleTochkaWebhook(
            """{"payment_id":"$tochkaId","payment_link_id":"${response.paymentId}","status":"paid"}""",
            "test-signature",
        )

        val status = subscriptionService.getStatus(TestFixtures.guestActor())
        assertTrue(status.isPro)
        assertEquals(SubscriptionServiceImpl.TARIFF, status.tariff)
    }

    @Test
    fun `webhook with invalid signature rejected`() = runTest {
        assertFailsWith<BadRequestException> {
            service.handleTochkaWebhook("""{"payment_id":"x","status":"paid"}""", "wrong")
        }
    }

    @Test
    fun `webhook ignores non-paid status`() = runTest {
        val response = service.createTochkaPayment(deviceId, SubscriptionServiceImpl.TARIFF)
        val tochkaId = "tochka_${response.paymentId.toString().take(8)}"

        service.handleTochkaWebhook(
            """{"payment_id":"$tochkaId","payment_link_id":"${response.paymentId}","status":"pending"}""",
            "test-signature",
        )

        assertTrue(!subscriptionService.getStatus(TestFixtures.guestActor()).isPro)
    }
}
