package ru.kkalscan.integrations.tochka

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import ru.kkalscan.AppConfig
import ru.kkalscan.domain.BadRequestException

class TochkaCustomerCodeResolver(
    private val httpClient: HttpClient,
    private val accessToken: String = AppConfig.tochkaAccessToken,
    private val apiBaseUrl: String = AppConfig.tochkaApiBaseUrl,
    private val envOverride: String = AppConfig.tochkaCustomerCodeOverride,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    private val log = LoggerFactory.getLogger(TochkaCustomerCodeResolver::class.java)

    @Volatile
    private var cached: String? = null

    suspend fun resolve(): String {
        envOverride.takeIf { it.isNotBlank() }?.let { return it }
        cached?.let { return it }
        return fetchBusinessCustomerCode().also {
            cached = it
            log.info("Tochka customerCode resolved: {}", it)
        }
    }

    internal suspend fun fetchBusinessCustomerCode(): String {
        val response = httpClient.get("$apiBaseUrl/uapi/open-banking/v1.0/customers") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header(HttpHeaders.Accept, "application/json")
        }

        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            log.warn("Tochka customers list failed: HTTP {} {}", response.status.value, body.take(500))
            throw BadRequestException("Не удалось получить customerCode из Точки")
        }

        return parseCustomerCode(json.parseToJsonElement(body).jsonObject)
    }

    internal fun parseCustomerCode(root: JsonObject): String {
        val data = root["Data"]?.jsonObject
            ?: throw BadRequestException("Точка не вернула список клиентов")

        data.stringField("customerCode")?.let { return it }

        val customers = data["Customer"]?.let(::asCustomerObjects).orEmpty()
        if (customers.isEmpty()) {
            throw BadRequestException("В Точке не найден customerCode")
        }

        customers.firstOrNull { customer ->
            customer.stringField("customerType")?.equals("Business", ignoreCase = true) == true
        }?.stringField("customerCode")?.let { return it }

        if (customers.size == 1) {
            return customers.first().stringField("customerCode")
                ?: throw BadRequestException("В Точке не найден customerCode")
        }

        throw BadRequestException(
            "Несколько клиентов в Точке — задайте TOCHKA_CUSTOMER_CODE вручную",
        )
    }

    private fun asCustomerObjects(element: JsonElement): List<JsonObject> = when (element) {
        is JsonArray -> element.mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(element)
        else -> emptyList()
    }

    private fun JsonObject.stringField(name: String): String? =
        this[name]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
}
