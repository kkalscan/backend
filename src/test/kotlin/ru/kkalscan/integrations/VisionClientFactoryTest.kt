package ru.kkalscan.integrations

import kotlin.test.Test
import kotlin.test.assertTrue

class VisionClientFactoryTest {
    @Test
    fun `default provider is stub`() {
        val client = VisionClientFactory.create()
        assertTrue(client is StubVisionClient)
    }
}
