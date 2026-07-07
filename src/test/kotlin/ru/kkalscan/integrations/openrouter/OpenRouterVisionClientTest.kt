package ru.kkalscan.integrations.openrouter

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenRouterVisionClientTest {
    @Test
    fun `analyzeFood calls openrouter and parses dishes`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/api/v1/chat/completions", request.url.encodedPath)
            assertEquals("Bearer test-key", request.headers[HttpHeaders.Authorization])
            respond(
                content = """
                {
                  "choices": [{
                    "message": {
                      "content": "{\"dishes\":[{\"name\":\"Салат\",\"grams\":150,\"kcal\":90,\"protein\":3,\"fat\":5,\"carbs\":8}]}"
                    }
                  }]
                }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = OpenRouterVisionClient(
            httpClient = HttpClient(mockEngine),
            apiKey = "test-key",
            model = "google/gemini-2.5-flash",
            baseUrl = "https://openrouter.ai/api/v1",
        )

        val dishes = client.analyzeFood(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
        assertEquals("Салат", dishes.single().name)
        assertEquals(90, dishes.single().kcal)
    }

    @Test
    fun `analyzeWorkout calls openrouter and parses result`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/api/v1/chat/completions", request.url.encodedPath)
            respond(
                content = """
                {
                  "choices": [{
                    "message": {
                      "content": "{\"title\":\"Бег\",\"burned_kcal\":280,\"duration_minutes\":30}"
                    }
                  }]
                }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = OpenRouterVisionClient(
            httpClient = HttpClient(mockEngine),
            apiKey = "test-key",
            model = "google/gemini-2.5-flash",
            baseUrl = "https://openrouter.ai/api/v1",
        )

        val result = client.analyzeWorkout("бег 30 минут")
        assertEquals("Бег", result.title)
        assertEquals(280, result.burnedKcal)
        assertEquals(30, result.durationMinutes)
    }
}
