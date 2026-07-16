package ru.kkalscan.integrations

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StubVisionClientIntentTest {
    private val client = StubVisionClient()

    @Test
    fun borscht_isFood() = runBlocking {
        assertTrue(client.classifySearchIntent("борщ"))
    }

    @Test
    fun profile_isNotFood() = runBlocking {
        assertFalse(client.classifySearchIntent("профиль"))
    }

    @Test
    fun garbage_isNotFood() = runBlocking {
        assertFalse(client.classifySearchIntent("xyzunknown123"))
    }
}
