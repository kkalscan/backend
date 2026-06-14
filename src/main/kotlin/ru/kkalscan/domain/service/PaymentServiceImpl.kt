package ru.kkalscan.domain.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        val tochka = tochkaClient.createPayment(
            amountKopecks = PRO_PRICE_KOPECKS,
            description = "KkalScan Pro 199 ₽/мес",
            metadata = mapOf("device_id" to deviceId.toString(), "tariff" to tariff),
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
        if (!tochkaClient.verifyWebhookSignature(rawBody, signature)) {
            throw BadRequestException("Invalid webhook signature")
        }

        val json = Json.parseToJsonElement(rawBody).jsonObject
        val tochkaId = json["payment_id"]?.jsonPrimitive?.content
            ?: json["id"]?.jsonPrimitive?.content
            ?: throw BadRequestException("Missing payment_id")
        val status = json["status"]?.jsonPrimitive?.content ?: "unknown"

        if (status != "paid" && status != "SUCCESS") return

        val payment = paymentRepository.findByTochkaId(tochkaId) ?: return
        if (payment.status == "paid") return

        val paidAt = Instant.now()
        paymentRepository.markPaid(payment.id, tochkaId, paidAt)
        subscriptionService.activatePro(payment.deviceId, payment.tariff, paidAt)
    }

    override suspend fun renderPayPage(deviceId: UUID): String {
        deviceRepository.getOrCreate(deviceId)
        val response = createTochkaPayment(deviceId, TARIFF)
        return """
            <!DOCTYPE html>
            <html lang="ru">
            <head><meta charset="utf-8"><title>KkalScan Pro</title></head>
            <body>
              <h1>KkalScan Pro — 199 ₽/мес</h1>
              <p>Безлимитные сканы калорий по фото.</p>
              <a href="${response.paymentUrl}">Оплатить</a>
            </body>
            </html>
        """.trimIndent()
    }

    companion object {
        const val PRO_PRICE_KOPECKS = 19_900
    }
}
