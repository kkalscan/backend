package ru.kkalscan.integrations

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class VisionClientFactoryTest {
    @Test
    fun `default provider is stub`() {
        val client = VisionClientFactory.create()
        assertTrue(client is StubVisionClient)
    }

    @Test
    fun `stub vision returns fiber in dishes`() = runTest {
        val dishes = StubVisionClient().analyzeFood(byteArrayOf(1, 2, 3))
        assertTrue(dishes.isNotEmpty())
        assertTrue(dishes.all { it.fiber > 0.0 })
    }
}
