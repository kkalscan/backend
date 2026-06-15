package ru.kkalscan.integrations.tochka

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import ru.kkalscan.AppConfig
import ru.kkalscan.domain.BadRequestException
import ru.kkalscan.domain.port.TochkaClient

class HttpTochkaClient(
    private val httpClient: HttpClient,
    private val accessToken: String = AppConfig.tochkaAccessToken,
    private val merchantId: String = AppConfig.tochkaMerchantId,
    private val apiBaseUrl: String = AppConfig.tochkaApiBaseUrl,
    private val customerCodeResolver: TochkaCustomerCodeResolver = TochkaCustomerCodeResolver(
        httpClient = httpClient,
        accessToken = accessToken,
        apiBaseUrl = apiBaseUrl,
    ),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : TochkaClient {

    private val log = LoggerFactory.getLogger(HttpTochkaClient::class.java)

    override suspend fun createPayment(
        amountKopecks: Int,
        description: String,
        metadata: Map<String, String>,
    ): TochkaClient.TochkaPayment {
        val amountRub = "%.2f".format(amountKopecks / 100.0)
        val paymentLinkId = metadata["payment_link_id"]
            ?: throw BadRequestException("payment_link_id обязателен")
        val customerCode = customerCodeResolver.resolve()

        val payload = CreatePaymentRequest(
            data = CreatePaymentData(
                customerCode = customerCode,
                amount = amountRub,
                purpose = description,
                paymentMode = listOf("card", "sbp"),
                redirectUrl = metadata["redirect_url"],
                failRedirectUrl = metadata["fail_redirect_url"],
                merchantId = merchantId.takeIf { it.isNotBlank() },
                paymentLinkId = paymentLinkId,
            ),
        )

        val response = httpClient.post("$apiBaseUrl/uapi/acquiring/v1.0/payments") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header(HttpHeaders.Accept, "application/json")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreatePaymentRequest.serializer(), payload))
        }

        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            log.warn("Tochka create payment failed: HTTP {} {}", response.status.value, body.take(500))
            throw BadRequestException("Не удалось создать платёж в Точке")
        }

        val data = json.parseToJsonElement(body).jsonObject["Data"]?.jsonObject
            ?: throw BadRequestException("Некорректный ответ Точки")

        val operationId = data.stringField("operationId")
            ?: throw BadRequestException("Точка не вернула operationId")
        val paymentUrl = data.stringField("paymentLink")
            ?: data.stringField("paymentUrl")
            ?: throw BadRequestException("Точка не вернула paymentLink")

        log.info("Tochka payment created: operationId={} paymentLinkId={}", operationId, paymentLinkId)
        return TochkaClient.TochkaPayment(id = operationId, paymentUrl = paymentUrl)
    }

    override fun parseWebhook(rawBody: String, signature: String?): TochkaClient.TochkaWebhookEvent? =
        TochkaWebhookVerifier.parseWebhook(rawBody)

    private fun JsonObject.stringField(name: String): String? =
        this[name]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

    @Serializable
    private data class CreatePaymentRequest(
        @SerialName("Data") val data: CreatePaymentData,
    )

    @Serializable
    private data class CreatePaymentData(
        val customerCode: String,
        val amount: String,
        val purpose: String,
        val paymentMode: List<String>,
        val redirectUrl: String? = null,
        val failRedirectUrl: String? = null,
        val merchantId: String? = null,
        val paymentLinkId: String? = null,
    )
}
