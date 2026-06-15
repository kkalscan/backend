package ru.kkalscan.domain.service

import ru.kkalscan.AppConfig
import ru.kkalscan.domain.BadRequestException
import ru.kkalscan.domain.port.DeviceRepository
import ru.kkalscan.domain.port.PaymentCreateResponse
import ru.kkalscan.domain.port.PaymentRecord
import ru.kkalscan.domain.port.PaymentRepository
import ru.kkalscan.domain.port.PaymentService
import ru.kkalscan.domain.port.SubscriptionService
import ru.kkalscan.domain.port.TochkaClient
import ru.kkalscan.domain.service.SubscriptionServiceImpl.Companion.TARIFF
import java.time.Instant
import java.util.UUID

class PaymentServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val deviceRepository: DeviceRepository,
    private val subscriptionService: SubscriptionService,
    private val tochkaClient: TochkaClient,
) : PaymentService {

    override suspend fun createTochkaPayment(deviceId: UUID, tariff: String): PaymentCreateResponse {
        deviceRepository.getOrCreate(deviceId)
        if (tariff != TARIFF) throw BadRequestException("Неизвестный тариф")

        val paymentId = UUID.randomUUID()
        val baseUrl = AppConfig.publicBaseUrl.trimEnd('/')
        val tochka = tochkaClient.createPayment(
            amountKopecks = PRO_PRICE_KOPECKS,
            description = "KkalScan Pro 15 ₽ (тест)",
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
              <h1>KkalScan Pro — 15 ₽ (тест)</h1>
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
        const val PRO_PRICE_KOPECKS = 1_500
    }
}
