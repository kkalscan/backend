package ru.kkalscan.integrations.tochka

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.kkalscan.domain.port.TochkaClient
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

object TochkaWebhookVerifier {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val publicKey: RSAPublicKey by lazy { loadTochkaPublicKey() }

    fun parseWebhook(rawBody: String): TochkaClient.TochkaWebhookEvent? {
        val trimmed = rawBody.trim()
        if (!trimmed.startsWith("eyJ")) return null

        return runCatching {
            val algorithm = Algorithm.RSA256(publicKey, null)
            val verifier = JWT.require(algorithm).build()
            val decoded = verifier.verify(trimmed)
            val payload = json.parseToJsonElement(decoded.payload).jsonObject
            payload.toWebhookEvent()
        }.getOrNull()
    }

    private fun JsonObject.toWebhookEvent(): TochkaClient.TochkaWebhookEvent =
        TochkaClient.TochkaWebhookEvent(
            operationId = stringField("operationId"),
            paymentLinkId = stringField("paymentLinkId"),
            status = stringField("status") ?: "unknown",
            webhookType = stringField("webhookType"),
        )

    private fun JsonObject.stringField(name: String): String? =
        this[name]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

    private fun loadTochkaPublicKey(): RSAPublicKey {
        // https://enter.tochka.com/doc/openapi/static/keys/public
        val modulus = base64UrlToBigInteger(
            "rwm77av7GIttq-JF1itEgLCGEZW_zz16RlUQVYlLbJtyRSu61fCec_rroP6PxjXU2uLzUOaGaLgAPeUZAJrGuVp9nryKgbZceHckdHDYgJd9TsdJ1MYUsXaOb9joN9vmsCscBx1lwSlFQyNQsHUsrjuDk-opf6RCuazRQ9gkoDCX70HV8WBMFoVm-YWQKJHZEaIQxg_DU4gMFyKRkDGKsYKA0POL-UgWA1qkg6nHY5BOMKaqxbc5ky87muWB5nNk4mfmsckyFv9j1gBiXLKekA_y4UwG2o1pbOLpJS3bP_c95rm4M9ZBmGXqfOQhbjz8z-s9C11i-jmOQ2ByohS-ST3E5sqBzIsxxrxyQDTw--bZNhzpbciyYW4GfkkqyeYoOPd_84jPTBDKQXssvj8ZOj2XboS77tvEO1n1WlwUzh8HPCJod5_fEgSXuozpJtOggXBv0C2ps7yXlDZf-7Jar0UYc_NJEHJF-xShlqd6Q3sVL02PhSCM-ibn9DN9BKmD",
        )
        val exponent = base64UrlToBigInteger("AQAB")
        val spec = RSAPublicKeySpec(modulus, exponent)
        return KeyFactory.getInstance("RSA").generatePublic(spec) as RSAPublicKey
    }

    private fun base64UrlToBigInteger(value: String): BigInteger {
        val decoded = Base64.getUrlDecoder().decode(value)
        return BigInteger(1, decoded)
    }
}
