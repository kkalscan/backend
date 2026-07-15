package ru.kkalscan.domain.service

import kotlinx.coroutines.test.runTest
import ru.kkalscan.TestFixtures
import ru.kkalscan.data.memory.InMemoryRepositories
import ru.kkalscan.domain.BadRequestException
import ru.kkalscan.domain.port.PromoCode
import ru.kkalscan.domain.port.TochkaClient
import ru.kkalscan.integrations.LoggingPlainTextMailer
import ru.kkalscan.integrations.StubTochkaClient
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Captures last createPayment metadata for assertions. */
private class RecordingTochkaClient : TochkaClient {
    var lastMetadata: Map<String, String>? = null
        private set

    override suspend fun createPayment(
        amountKopecks: Int,
        description: String,
        metadata: Map<String, String>,
    ): TochkaClient.TochkaPayment {
        lastMetadata = metadata
        return StubTochkaClient().createPayment(amountKopecks, description, metadata)
    }

    override fun parseWebhook(rawBody: String, signature: String?): TochkaClient.TochkaWebhookEvent? =
        StubTochkaClient().parseWebhook(rawBody, signature)
}

class PaymentServiceTest {
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

    private fun paidService(free: Boolean) = PaymentServiceImpl(
        repos.payments,
        repos.devices,
        subscriptionService,
        StubTochkaClient(),
        mailer,
        promoService,
        testPaymentNotifyTo = "owner@example.com",
        testPaymentSecret = "test-secret",
        freeProActivationEnabled = free,
    )

    private fun serviceWith(
        tochka: TochkaClient,
        publicBaseUrl: String,
    ) = PaymentServiceImpl(
        repos.payments,
        repos.devices,
        subscriptionService,
        tochka,
        mailer,
        promoService,
        testPaymentNotifyTo = "owner@example.com",
        testPaymentSecret = "test-secret",
        freeProActivationEnabled = false,
        publicBaseUrl = publicBaseUrl,
    )

    @Test
    fun `start pro subscription activates pro in free mode`() = runTest {
        val service = paidService(free = true)

        val result = service.startProSubscription(deviceId, TariffCatalog.MONTHLY_ID)

        assertTrue(result.isPro)
        assertEquals(false, result.paymentRequired)
        assertTrue(subscriptionService.getStatus(TestFixtures.guestActor()).isPro)
    }

    @Test
    fun `start pro subscription returns payment url when free mode disabled`() = runTest {
        val service = paidService(free = false)

        val result = service.startProSubscription(deviceId, TariffCatalog.MONTHLY_ID)

        assertTrue(result.paymentRequired)
        assertTrue(!result.paymentUrl.isNullOrBlank())
        assertTrue(!subscriptionService.getStatus(TestFixtures.guestActor()).isPro)
    }

    @Test
    fun `create payment returns stub url`() = runTest {
        val response = service.createTochkaPayment(deviceId, TariffCatalog.MONTHLY_ID)

        assertTrue(response.paymentUrl.contains("pay.tochka.example"))
        assertTrue(response.paymentId.toString().isNotBlank())
    }

    @Test
    fun `create payment omits redirects when public base url is http`() = runTest {
        val recording = RecordingTochkaClient()
        val svc = serviceWith(recording, publicBaseUrl = "http://91.207.75.72:8080")

        svc.createTochkaPayment(deviceId, TariffCatalog.MONTHLY_ID)

        val meta = assertNotNull(recording.lastMetadata)
        assertFalse(meta.containsKey("redirect_url"))
        assertFalse(meta.containsKey("fail_redirect_url"))
    }

    @Test
    fun `create payment includes https redirects when public base url is https`() = runTest {
        val recording = RecordingTochkaClient()
        val svc = serviceWith(recording, publicBaseUrl = "https://pay.example.com")

        svc.createTochkaPayment(deviceId, TariffCatalog.MONTHLY_ID)

        val meta = assertNotNull(recording.lastMetadata)
        assertEquals(
            "https://pay.example.com/pay/success?device_id=$deviceId",
            meta["redirect_url"],
        )
        assertEquals(
            "https://pay.example.com/pay/fail?device_id=$deviceId",
            meta["fail_redirect_url"],
        )
    }

    @Test
    fun `create payment uses catalog price 200 rub`() = runTest {
        val response = service.createTochkaPayment(deviceId, TariffCatalog.MONTHLY_ID)
        val payment = repos.payments.findById(response.paymentId)
        assertEquals(20_000, payment?.amountKopecks)
    }

    @Test
    fun `create payment applies bound promo discount`() = runTest {
        promoService.applyPromo(deviceId, "Lida")
        val response = service.createTochkaPayment(deviceId, TariffCatalog.MONTHLY_ID)
        val payment = repos.payments.findById(response.paymentId)
        assertEquals(10_000, payment?.amountKopecks)
    }

    @Test
    fun `lifetime free activation sets far pro until`() = runTest {
        val service = paidService(free = true)
        val before = Instant.now()

        val result = service.startProSubscription(deviceId, TariffCatalog.LIFETIME_ID)

        assertTrue(result.isPro)
        val until = assertNotNull(result.proUntil)
        assertTrue(until.isAfter(before.plus(365 * 50L, ChronoUnit.DAYS)))
    }

    @Test
    fun `pay page activates pro in free mode`() = runTest {
        val service = paidService(free = true)

        val html = service.renderPayPage(deviceId)

        assertTrue(html.contains("Спасибо"))
        assertTrue(subscriptionService.getStatus(TestFixtures.guestActor()).isPro)
    }

    @Test
    fun `unknown tariff rejected`() = runTest {
        assertFailsWith<BadRequestException> {
            service.createTochkaPayment(deviceId, "unknown")
        }
    }

    @Test
    fun `paid webhook activates pro`() = runTest {
        val response = service.createTochkaPayment(deviceId, TariffCatalog.MONTHLY_ID)
        val tochkaId = "tochka_${response.paymentId.toString().take(8)}"

        service.handleTochkaWebhook(
            """{"payment_id":"$tochkaId","payment_link_id":"${response.paymentId}","status":"paid"}""",
            "test-signature",
        )

        val status = subscriptionService.getStatus(TestFixtures.guestActor())
        assertTrue(status.isPro)
        assertEquals(TariffCatalog.MONTHLY_ID, status.tariff)
    }

    @Test
    fun `webhook with invalid signature rejected`() = runTest {
        assertFailsWith<BadRequestException> {
            service.handleTochkaWebhook("""{"payment_id":"x","status":"paid"}""", "wrong")
        }
    }

    @Test
    fun `webhook ignores non-paid status`() = runTest {
        val response = service.createTochkaPayment(deviceId, TariffCatalog.MONTHLY_ID)
        val tochkaId = "tochka_${response.paymentId.toString().take(8)}"

        service.handleTochkaWebhook(
            """{"payment_id":"$tochkaId","payment_link_id":"${response.paymentId}","status":"pending"}""",
            "test-signature",
        )

        assertTrue(!subscriptionService.getStatus(TestFixtures.guestActor()).isPro)
    }

    @Test
    fun `start after promo bind charges discounted without reentering code`() = runTest {
        promoService.applyPromo(deviceId, "Lida")
        val tochkaService = paidService(free = false)

        val paid = tochkaService.createTochkaPayment(deviceId, TariffCatalog.LIFETIME_ID)

        assertEquals(250_000, repos.payments.findById(paid.paymentId)?.amountKopecks)
    }
}

class PromoServiceTest {
    private val repos = InMemoryRepositories()
    private val promoService = PromoService(repos.promoCodes, repos.devicePromoBindings)
    private val deviceId = TestFixtures.deviceId

    @Test
    fun `offers without promo are list prices`() {
        val offers = promoService.listOffers(deviceId)
        assertEquals(2, offers.size)
        assertEquals(200, offers.first { it.tariff == TariffCatalog.MONTHLY_ID }.amountRub)
        assertEquals(5000, offers.first { it.tariff == TariffCatalog.LIFETIME_ID }.amountRub)
        assertEquals(0, offers.first().discountPercent)
        assertNull(offers.first().promoCode)
    }

    @Test
    fun `apply Lida halves offer amounts`() {
        promoService.applyPromo(deviceId, "Lida")
        val offers = promoService.listOffers(deviceId)
        assertEquals(100, offers.first { it.tariff == TariffCatalog.MONTHLY_ID }.amountRub)
        assertEquals(2500, offers.first { it.tariff == TariffCatalog.LIFETIME_ID }.amountRub)
        assertEquals(50, offers.first().discountPercent)
        assertEquals("Lida", offers.first().promoCode)
    }

    @Test
    fun `unknown promo rejected`() {
        assertFailsWith<BadRequestException> {
            promoService.applyPromo(deviceId, "nope")
        }
        assertNull(repos.devicePromoBindings.getBoundCode(deviceId))
    }

    @Test
    fun `second seed code works without client change`() {
        repos.promoCodes.upsert(PromoCode(code = "Friends", discountPercent = 20, active = true))
        promoService.applyPromo(deviceId, "friends")
        val offers = promoService.listOffers(deviceId)
        assertEquals(160, offers.first { it.tariff == TariffCatalog.MONTHLY_ID }.amountRub)
        assertEquals(4000, offers.first { it.tariff == TariffCatalog.LIFETIME_ID }.amountRub)
    }

    @Test
    fun `lida lookup is case insensitive`() {
        val result = promoService.applyPromo(deviceId, "lida")
        assertEquals("Lida", result.promoCode)
        assertEquals(50, result.discountPercent)
    }
}
