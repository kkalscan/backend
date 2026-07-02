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
import ru.kkalscan.domain.service.SubscriptionServiceImpl.Companion.PRO_DAYS
import ru.kkalscan.domain.service.SubscriptionServiceImpl.Companion.TARIFF
import java.time.temporal.ChronoUnit
import java.time.Instant
import java.util.UUID

class PaymentServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val deviceRepository: DeviceRepository,
    private val subscriptionService: SubscriptionService,
    private val tochkaClient: TochkaClient,
    private val plainTextMailer: PlainTextMailer,
    private val testPaymentNotifyTo: String = AppConfig.testPaymentNotifyTo,
    private val testPaymentSecret: String = AppConfig.testPaymentSecret,
    private val testPaymentEnabled: Boolean = AppConfig.testPaymentEnabled,
    private val freeProActivationEnabled: Boolean = AppConfig.freeProActivationEnabled,
) : PaymentService {

    override suspend fun startProSubscription(deviceId: UUID, tariff: String): PaymentService.ProSubscriptionStartResult {
        deviceRepository.getOrCreate(deviceId)
        if (tariff != TARIFF) throw BadRequestException("Неизвестный тариф")

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
        val paidAt = Instant.now()
        val paymentId = UUID.randomUUID()
        paymentRepository.create(
            PaymentRecord(
                id = paymentId,
                deviceId = deviceId,
                userId = deviceRepository.findById(deviceId)?.userId,
                tochkaPaymentId = "free_$paymentId",
                amountKopecks = 0,
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
            message = "Pro активирован на ${PRO_DAYS} дней",
        )
    }

    override suspend fun createTochkaPayment(deviceId: UUID, tariff: String): PaymentCreateResponse {
        deviceRepository.getOrCreate(deviceId)
        if (tariff != TARIFF) throw BadRequestException("Неизвестный тариф")

        val paymentId = UUID.randomUUID()
        val baseUrl = AppConfig.publicBaseUrl.trimEnd('/')
        val tochka = tochkaClient.createPayment(
            amountKopecks = PRO_PRICE_KOPECKS,
            description = "KkalScan Pro — ${PRO_PRICE_RUB} ₽/мес",
            metadata = mapOf(
                "device_id" to deviceId.toString(),
                "tariff" to tariff,
                "payment_link_id" to paymentId.toString(),
                "redirect_url" to "$baseUrl/pay/success?device_id=$deviceId",
                "fail_redirect_url" to "$baseUrl/pay/fail?device_id=$deviceId",
            ),
        )

        paymentRepository.create(
            PaymentRecord(
                id = paymentId,
                deviceId = deviceId,
                userId = deviceRepository.findById(deviceId)?.userId,
                tochkaPaymentId = tochka.id,
                amountKopecks = PRO_PRICE_KOPECKS,
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
                    appendLine("Тариф: $TARIFF")
                    appendLine("Сумма: ${PRO_PRICE_RUB} ₽")
                    appendLine("Оплачено: $paidAt")
                    appendLine("Срок: ${PRO_DAYS} дней")
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
                amountKopecks = PRO_PRICE_KOPECKS,
                tariff = TARIFF,
                status = "test_paid",
                paidAt = paidAt,
            ),
        )
        subscriptionService.activatePro(deviceId, TARIFF, paidAt)

        val status = subscriptionService.getStatus(
            Actor(
                deviceId = deviceId,
                userId = deviceRepository.findById(deviceId)?.userId,
                isPro = true,
                accountLinked = false,
                linkedProviders = emptyList(),
            ),
        )
        val proUntil = status.proUntil ?: paidAt.plus(PRO_DAYS, ChronoUnit.DAYS)

        return PaymentService.TestPaymentResult(
            isPro = status.isPro,
            proUntil = proUntil,
            tariff = TARIFF,
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
            activateFreePro(deviceId, TARIFF)
            return renderPaySuccessPage(deviceId)
        }
        val response = createTochkaPayment(deviceId, TARIFF)
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
              <h1>KkalScan Pro — ${PRO_PRICE_RUB} ₽/мес</h1>
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

    companion object {
        const val PRO_PRICE_KOPECKS = 19_900
        const val PRO_PRICE_RUB = 199
    }
}
