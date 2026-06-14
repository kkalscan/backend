package ru.kkalscan.integrations

import ru.kkalscan.domain.model.DishDto
import ru.kkalscan.domain.port.TochkaClient
import ru.kkalscan.domain.port.VisionClient
import ru.kkalscan.domain.port.VkAuthClient

class StubVisionClient(
    private val dishes: List<DishDto> = listOf(
        DishDto("Тестовое блюдо", 200, 350, 15.0, 10.0, 40.0),
    ),
) : VisionClient {
    override suspend fun analyzeFood(imageBytes: ByteArray): List<DishDto> = dishes
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
        val id = "tochka_${metadata["device_id"]?.take(8) ?: "x"}"
        return TochkaClient.TochkaPayment(
            id = id,
            paymentUrl = "https://pay.tochka.example/$id",
        )
    }

    override fun verifyWebhookSignature(body: String, signature: String?): Boolean =
        signature == "test-signature" || signature == null
}
