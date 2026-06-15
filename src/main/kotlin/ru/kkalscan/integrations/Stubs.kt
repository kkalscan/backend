package ru.kkalscan.integrations

import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import ru.kkalscan.domain.model.DishDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.kkalscan.domain.port.TochkaClient
import ru.kkalscan.domain.port.VisionClient
import ru.kkalscan.domain.port.VkAuthClient
import kotlin.math.absoluteValue

/**
 * Offline vision stub for dev/MVP — no OpenRouter calls.
 * Picks a preset from image bytes so different photos get different dishes.
 */
class StubVisionClient : VisionClient {

    private val log = LoggerFactory.getLogger(StubVisionClient::class.java)

    private val presets: List<List<DishDto>> = listOf(
        listOf(DishDto("Борщ с говядиной", 300, 250, 12.0, 8.0, 22.0)),
        listOf(DishDto("Куриная грудка с рисом", 350, 420, 45.0, 6.0, 52.0)),
        listOf(
            DishDto("Салат Цезарь", 200, 280, 14.0, 18.0, 12.0),
            DishDto("Капучино", 250, 80, 4.0, 3.0, 8.0),
        ),
        listOf(DishDto("Овсянка с бананом", 250, 320, 12.0, 8.0, 52.0)),
        listOf(DishDto("Плов с бараниной", 320, 480, 22.0, 16.0, 58.0)),
    )

    override suspend fun analyzeFood(imageBytes: ByteArray): List<DishDto> {
        delay(STUB_LATENCY_MS)
        val index = (imageBytes.sumOf { it.toInt() }.absoluteValue) % presets.size
        val dishes = presets[index]
        log.info(
            "stub vision: {} bytes -> preset {} ({})",
            imageBytes.size,
            index,
            dishes.joinToString { it.name },
        )
        return dishes
    }

    private companion object {
        const val STUB_LATENCY_MS = 900L
    }
}

class StubVkAuthClient : VkAuthClient {
    override suspend fun verifyToken(accessToken: String): VkAuthClient.VkUser {
        val id = accessToken.removePrefix("vk_test_").toLongOrNull()
            ?: throw IllegalArgumentException("invalid token")
        return VkAuthClient.VkUser(id)
    }
}

class StubTochkaClient : TochkaClient {
    override suspend fun createPayment(
        amountKopecks: Int,
        description: String,
        metadata: Map<String, String>,
    ): TochkaClient.TochkaPayment {
        val paymentLinkId = metadata["payment_link_id"]?.take(8) ?: "x"
        val id = "tochka_$paymentLinkId"
        return TochkaClient.TochkaPayment(
            id = id,
            paymentUrl = "https://pay.tochka.example/$id",
        )
    }

    override fun parseWebhook(rawBody: String, signature: String?): TochkaClient.TochkaWebhookEvent? {
        if (signature != null && signature != "test-signature") return null
        if (!rawBody.trimStart().startsWith("{")) return null

        val json = Json.parseToJsonElement(rawBody).jsonObject
        return TochkaClient.TochkaWebhookEvent(
            operationId = json["payment_id"]?.jsonPrimitive?.content
                ?: json["operationId"]?.jsonPrimitive?.content,
            paymentLinkId = json["payment_link_id"]?.jsonPrimitive?.content
                ?: json["paymentLinkId"]?.jsonPrimitive?.content,
            status = json["status"]?.jsonPrimitive?.content ?: "unknown",
            webhookType = json["webhookType"]?.jsonPrimitive?.content,
        )
    }
}
