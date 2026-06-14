package ru.kkalscan.routes

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.kkalscan.TestFixtures
import ru.kkalscan.testModule
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiRoutesTest {
    private val deviceId = TestFixtures.deviceId.toString()
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `health returns ok`() = testApplication {
        application { testModule() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `scan and diary flow`() = testApplication {
        application { testModule(TestFixtures.freshModule()) }

        val scanResponse = client.post("/api/v1/scan") {
            header("X-Device-Id", deviceId)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("photo", "fake".toByteArray(), Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=food.jpg")
                        })
                    },
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, scanResponse.status)
        val scanBody = json.parseToJsonElement(scanResponse.bodyAsText()).jsonObject
        val scanId = scanBody["scan_id"]!!.jsonPrimitive.content
        assertEquals(3, scanBody["scans_left"]!!.jsonPrimitive.int)

        val diaryResponse = client.post("/api/v1/diary/entries") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "device_id": "$deviceId",
                  "meal_type": "lunch",
                  "scan_id": "$scanId"
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.Created, diaryResponse.status)
        val diaryBody = json.parseToJsonElement(diaryResponse.bodyAsText()).jsonObject
        assertEquals(2, diaryBody["scans_left"]!!.jsonPrimitive.int)
    }

    @Test
    fun `scan bonus returns extra scans`() = testApplication {
        application { testModule(TestFixtures.freshModule()) }
        val response = client.post("/api/v1/scan/bonus") {
            contentType(ContentType.Application.Json)
            setBody("""{"device_id":"$deviceId"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(5, body["scans_left"]!!.jsonPrimitive.int)
    }

    @Test
    fun `invalid scan_id returns bad request`() = testApplication {
        application { testModule(TestFixtures.freshModule()) }
        val response = client.post("/api/v1/diary/entries") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "device_id": "$deviceId",
                  "meal_type": "lunch",
                  "scan_id": "not-a-uuid"
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
