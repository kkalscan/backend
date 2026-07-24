package ru.kkalscan.integrations.tochka

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TochkaWebhookVerifierTest {

    @Test
    fun `parses real Tochka acquiringInternetPayment JWT`() {
        // Captured from Tochka Send Webhook test (synthetic payload, public RSA signature).
        val jwt = javaClass.getResource("/tochka-webhook-sample.jwt")!!.readText().trim()

        val event = assertNotNull(TochkaWebhookVerifier.parseWebhook(jwt))
        assertEquals("acquiringInternetPayment", event.webhookType)
        assertEquals("APPROVED", event.status)
        assertEquals("beeac8a4-6047-3f38-8922-a664e6b5c43b", event.operationId)
        assertEquals("12345-ab-cd", event.paymentLinkId)
    }
}
