package ru.kkalscan.domain.service

import ru.kkalscan.AppConfig
import ru.kkalscan.domain.BadRequestException
import ru.kkalscan.domain.ForbiddenException
import ru.kkalscan.domain.port.DeviceRepository
import ru.kkalscan.domain.port.PlainTextMailer
import ru.kkalscan.domain.port.PaymentCreateResponse
import ru.kkalscan.domain.port.PaymentRecord
import ru.kkalscan.domain.port.PaymentRepository
import ru.kkalscan.domain.port.PaymentService
import ru.kkalscan.domain.port.SubscriptionService
import ru.kkalscan.domain.port.TochkaClient
import ru.kkalscan.domain.model.Actor
import java.time.temporal.ChronoUnit
import java.time.Instant
import java.util.UUID

class PaymentServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val deviceRepository: DeviceRepository,
    private val subscriptionService: SubscriptionService,
    private val tochkaClient: TochkaClient,
    private val plainTextMailer: PlainTextMailer,
    private val promoService: PromoService,
    private val testPaymentNotifyTo: String = AppConfig.testPaymentNotifyTo,
    private val testPaymentSecret: String = AppConfig.testPaymentSecret,
    private val testPaymentEnabled: Boolean = AppConfig.testPaymentEnabled,
    private val freeProActivationEnabled: Boolean = AppConfig.freeProActivationEnabled,
    private val publicBaseUrl: String = AppConfig.publicBaseUrl,
) : PaymentService {

    override suspend fun startProSubscription(deviceId: UUID, tariff: String): PaymentService.ProSubscriptionStartResult {
        deviceRepository.getOrCreate(deviceId)
        TariffCatalog.require(tariff)

        if (freeProActivationEnabled) {
            return activateFreePro(deviceId, tariff)
        }

        val payment = createTochkaPayment(deviceId, tariff)
        return PaymentService.ProSubscriptionStartResult(
            isPro = false,
            proUntil = null,
            tariff = tariff,
            paymentRequired = true,
            paymentUrl = payment.paymentUrl,
            paymentId = payment.paymentId,
        )
    }

    private suspend fun activateFreePro(deviceId: UUID, tariff: String): PaymentService.ProSubscriptionStartResult {
        val offer = TariffCatalog.require(tariff)
        val priced = resolveAmount(deviceId, offer)
        val paidAt = Instant.now()
        val paymentId = UUID.randomUUID()
        paymentRepository.create(
            PaymentRecord(
                id = paymentId,
                deviceId = deviceId,
                userId = deviceRepository.findById(deviceId)?.userId,
                tochkaPaymentId = "free_$paymentId",
                amountKopecks = priced.amountKopecks,
                tariff = tariff,
                status = "free_promo",
                paidAt = paidAt,
            ),
        )
        subscriptionService.activatePro(deviceId, tariff, paidAt)

        val status = subscriptionService.getStatus(
            Actor(
                deviceId = deviceId,
                userId = deviceRepository.findById(deviceId)?.userId,
                isPro = true,
                accountLinked = false,
                linkedProviders = emptyList(),
            ),
        )

        return PaymentService.ProSubscriptionStartResult(
            isPro = status.isPro,
            proUntil = status.proUntil,
            tariff = tariff,
            paymentRequired = false,
            message = "Pro активирован на ${offer.durationDays} дней",
        )
    }

    override suspend fun createTochkaPayment(deviceId: UUID, tariff: String): PaymentCreateResponse {
        deviceRepository.getOrCreate(deviceId)
        val offer = TariffCatalog.require(tariff)
        val priced = resolveAmount(deviceId, offer)

        val paymentId = UUID.randomUUID()
        val baseUrl = publicBaseUrl.trimEnd('/')
        val metadata = mutableMapOf(
            "device_id" to deviceId.toString(),
            "tariff" to tariff,
            "payment_link_id" to paymentId.toString(),
        )
        // Tochka rejects http(s) scheme other than https for redirect URLs.
        if (baseUrl.startsWith("https://", ignoreCase = true)) {
            metadata["redirect_url"] = "$baseUrl/pay/success?device_id=$deviceId"
            metadata["fail_redirect_url"] = "$baseUrl/pay/fail?device_id=$deviceId"
        }
        priced.promoCode?.let { metadata["promo_code"] = it }

        val tochka = tochkaClient.createPayment(
            amountKopecks = priced.amountKopecks,
            description = "${offer.title} — ${priced.amountRub} ₽",
            metadata = metadata,
        )

        paymentRepository.create(
            PaymentRecord(
                id = paymentId,
                deviceId = deviceId,
                userId = deviceRepository.findById(deviceId)?.userId,
                tochkaPaymentId = tochka.id,
                amountKopecks = priced.amountKopecks,
                tariff = tariff,
                status = "pending",
                paidAt = null,
            ),
        )

        return PaymentCreateResponse(paymentUrl = tochka.paymentUrl, paymentId = paymentId)
    }

    override suspend fun activateTestPayment(deviceId: UUID, secret: String): PaymentService.TestPaymentResult {
        if (!testPaymentEnabled) {
            throw ForbiddenException("Тестовая оплата отключена")
        }
        if (secret != testPaymentSecret) {
            throw ForbiddenException("Неверный секрет")
        }

        val offer = TariffCatalog.require(TariffCatalog.MONTHLY_ID)
        val priced = resolveAmount(deviceId, offer)
        deviceRepository.getOrCreate(deviceId)
        val paidAt = Instant.now()

        runCatching {
            plainTextMailer.send(
                to = testPaymentNotifyTo,
                subject = "KkalScan: тестовая оплата Pro",
                body = buildString {
                    appendLine("Тестовая оплата Pro активирована.")
                    appendLine()
                    appendLine("Device ID: $deviceId")
                    appendLine("Тариф: ${offer.id}")
                    appendLine("Сумма: ${priced.amountRub} ₽")
                    appendLine("Оплачено: $paidAt")
                    appendLine("Срок: ${offer.durationDays} дней")
                },
            )
        }.onFailure { e ->
            throw BadRequestException("Не удалось отправить письмо: ${e.message ?: "SMTP error"}")
        }

        val paymentId = UUID.randomUUID()
        paymentRepository.create(
            PaymentRecord(
                id = paymentId,
                deviceId = deviceId,
                userId = deviceRepository.findById(deviceId)?.userId,
                tochkaPaymentId = "test_$paymentId",
                amountKopecks = priced.amountKopecks,
                tariff = offer.id,
                status = "test_paid",
                paidAt = paidAt,
            ),
        )
        subscriptionService.activatePro(deviceId, offer.id, paidAt)

        val status = subscriptionService.getStatus(
            Actor(
                deviceId = deviceId,
                userId = deviceRepository.findById(deviceId)?.userId,
                isPro = true,
                accountLinked = false,
                linkedProviders = emptyList(),
            ),
        )
        val proUntil = status.proUntil ?: paidAt.plus(offer.durationDays, ChronoUnit.DAYS)

        return PaymentService.TestPaymentResult(
            isPro = status.isPro,
            proUntil = proUntil,
            tariff = offer.id,
            emailSent = true,
        )
    }

    override suspend fun handleTochkaWebhook(rawBody: String, signature: String?) {
        val event = tochkaClient.parseWebhook(rawBody, signature)
            ?: throw BadRequestException("Invalid webhook")

        if (event.webhookType != null && event.webhookType != "acquiringInternetPayment") return

        val status = event.status.uppercase()
        if (status != "PAID" && status != "SUCCESS" && status != "APPROVED") return

        val payment = event.paymentLinkId
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.let { paymentRepository.findById(it) }
            ?: event.operationId?.let { paymentRepository.findByTochkaId(it) }
            ?: return

        if (payment.status == "paid") return

        val tochkaId = event.operationId ?: payment.tochkaPaymentId ?: return
        val paidAt = Instant.now()
        paymentRepository.markPaid(payment.id, tochkaId, paidAt)
        subscriptionService.activatePro(payment.deviceId, payment.tariff, paidAt)
    }

    override suspend fun renderPayPage(deviceId: UUID): String {
        deviceRepository.getOrCreate(deviceId)
        if (freeProActivationEnabled) {
            activateFreePro(deviceId, TariffCatalog.MONTHLY_ID)
            return renderPaySuccessPage(deviceId)
        }
        val offer = TariffCatalog.require(TariffCatalog.MONTHLY_ID)
        val priced = resolveAmount(deviceId, offer)
        val response = createTochkaPayment(deviceId, offer.id)
        val paymentUrl = response.paymentUrl
        return """
            <!DOCTYPE html>
            <html lang="ru">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>KkalScan Pro</title>
              <meta http-equiv="refresh" content="0;url=$paymentUrl">
              <style>
                body { font-family: system-ui, sans-serif; max-width: 28rem; margin: 3rem auto; padding: 0 1rem; }
                a { color: #9B5CFF; }
              </style>
            </head>
            <body>
              <h1>${offer.title} — ${priced.amountRub} ₽</h1>
              <p>Безлимитные сканы калорий по фото. Оплата картой или СБП.</p>
              <p><a href="$paymentUrl">Перейти к оплате</a></p>
            </body>
            </html>
        """.trimIndent()
    }

    override suspend fun renderPaySuccessPage(deviceId: UUID): String =
        """
            <!DOCTYPE html>
            <html lang="ru">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Оплата прошла</title>
              <style>
                body { font-family: system-ui, sans-serif; max-width: 28rem; margin: 3rem auto; padding: 0 1rem; }
              </style>
            </head>
            <body>
              <h1>Спасибо!</h1>
              <p>Pro активируется в течение минуты. Вернитесь в приложение KkalScan — лимит сканов снимется автоматически.</p>
              <p><small>device: ${deviceId.toString().take(8)}…</small></p>
            </body>
            </html>
        """.trimIndent()

    override suspend fun renderPayFailPage(): String =
        """
            <!DOCTYPE html>
            <html lang="ru">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Оплата не прошла</title>
              <style>
                body { font-family: system-ui, sans-serif; max-width: 28rem; margin: 3rem auto; padding: 0 1rem; }
              </style>
            </head>
            <body>
              <h1>Оплата не прошла</h1>
              <p>Попробуйте ещё раз из приложения KkalScan.</p>
            </body>
            </html>
        """.trimIndent()

    private fun resolveAmount(deviceId: UUID, offer: TariffOffer): PricedOffer {
        val bound = promoService.getBoundPromo(deviceId)
        val discount = bound?.discountPercent ?: 0
        val amountKopecks = TariffCatalog.discountedKopecks(offer.priceKopecks, discount)
        return PricedOffer(
            amountKopecks = amountKopecks,
            amountRub = amountKopecks / 100,
            promoCode = bound?.promoCode?.takeIf { discount > 0 },
        )
    }

    private data class PricedOffer(
        val amountKopecks: Int,
        val amountRub: Int,
        val promoCode: String?,
    )
}
