package ru.kkalscan.domain.service

import kotlinx.coroutines.test.runTest
import ru.kkalscan.data.memory.InMemoryRepositories
import ru.kkalscan.domain.model.Actor
import ru.kkalscan.domain.port.PaymentRecord
import ru.kkalscan.integrations.LoggingPlainTextMailer
import ru.kkalscan.integrations.StubTochkaClient
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaymentSyncTest {
    private val repos = InMemoryRepositories()
    private val tochka = StubTochkaClient()
    private val subscription = SubscriptionServiceImpl(repos.devices, repos.users)
    private val promo = PromoService(repos.promoCodes, repos.devicePromoBindings)
    private val service = PaymentServiceImpl(
        repos.payments,
        repos.devices,
        subscription,
        tochka,
        LoggingPlainTextMailer(),
        promo,
        repos.promoPurchases,
        freeProActivationEnabled = false,
    )

    @Test
    fun `syncPendingPayments activates Pro when Tochka reports APPROVED`() = runTest {
        val deviceId = UUID.randomUUID()
        val paymentId = UUID.randomUUID()
        repos.devices.getOrCreate(deviceId)

        val created = tochka.createPayment(
            amountKopecks = 20_000,
            description = "KkalScan Pro",
            metadata = mapOf("payment_link_id" to paymentId.toString()),
        )
        repos.payments.create(
            PaymentRecord(
                id = paymentId,
                deviceId = deviceId,
                userId = null,
                tochkaPaymentId = created.id,
                amountKopecks = 20_000,
                tariff = TariffCatalog.MONTHLY_ID,
                status = "pending",
                paidAt = null,
                listAmountKopecks = 20_000,
            ),
        )
        tochka.markApproved(created.id, Instant.parse("2026-07-24T06:37:50Z"))

        val result = service.syncPendingPayments(deviceId)

        assertTrue(result.isPro)
        assertTrue(result.proUntil != null)
    }

    @Test
    fun `syncAllPendingPayments activates Pro without client call`() = runTest {
        val deviceId = UUID.randomUUID()
        val paymentId = UUID.randomUUID()
        repos.devices.getOrCreate(deviceId)

        val created = tochka.createPayment(
            amountKopecks = 20_000,
            description = "KkalScan Pro",
            metadata = mapOf("payment_link_id" to paymentId.toString()),
        )
        repos.payments.create(
            PaymentRecord(
                id = paymentId,
                deviceId = deviceId,
                userId = null,
                tochkaPaymentId = created.id,
                amountKopecks = 20_000,
                tariff = TariffCatalog.MONTHLY_ID,
                status = "pending",
                paidAt = null,
                listAmountKopecks = 20_000,
            ),
        )
        tochka.markApproved(created.id, Instant.parse("2026-07-24T06:37:50Z"))

        val activated = service.syncAllPendingPayments()

        assertEquals(1, activated)
        val status = subscription.getStatus(
            Actor(
                deviceId = deviceId,
                userId = null,
                isPro = false,
                accountLinked = false,
                linkedProviders = emptyList(),
            ),
        )
        assertTrue(status.isPro)
    }
}
