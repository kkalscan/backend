package ru.kkalscan.routes

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.kkalscan.TestFixtures
import ru.kkalscan.testModule
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun `privacy and pay pages return ok`() = testApplication {
        application { testModule(TestFixtures.freshModule()) }

        assertEquals(HttpStatusCode.OK, client.get("/privacy").status)
        val pay = client.get("/pay?device_id=$deviceId")
        assertEquals(HttpStatusCode.OK, pay.status)
        assertTrue(pay.bodyAsText().contains("KkalScan Pro"))
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
    fun `diary entry with dishes`() = testApplication {
        application { testModule(TestFixtures.freshModule()) }

        val response = client.post("/api/v1/diary/entries") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "device_id": "$deviceId",
                  "meal_type": "breakfast",
                  "dishes": [
                    {"name": "Овсянка", "grams": 200, "kcal": 150, "protein": 5.0, "fat": 3.0, "carbs": 27.0}
                  ]
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(150, body["entry"]!!.jsonObject["total_kcal"]!!.jsonPrimitive.int)
    }

    @Test
    fun `diary get and delete entry`() = testApplication {
        application { testModule(TestFixtures.freshModule()) }
        val today = LocalDate.now().toString()

        val create = client.post("/api/v1/diary/entries") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "device_id": "$deviceId",
                  "meal_type": "dinner",
                  "dishes": [
                    {"name": "Суп", "grams": 300, "kcal": 120, "protein": 6.0, "fat": 3.0, "carbs": 15.0}
                  ]
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.Created, create.status)
        val entryId = json.parseToJsonElement(create.bodyAsText())
            .jsonObject["entry"]!!.jsonObject["id"]!!.jsonPrimitive.content

        val day = client.get("/api/v1/diary?device_id=$deviceId&date=$today&timezone_offset_minutes=180")
        assertEquals(HttpStatusCode.OK, day.status)
        assertEquals(1, json.parseToJsonElement(day.bodyAsText()).jsonObject["entries"]!!.jsonArray.size)

        val delete = client.delete("/api/v1/diary/entries/$entryId") {
            header("X-Device-Id", deviceId)
        }
        assertEquals(HttpStatusCode.NoContent, delete.status)
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
        assertTrue(body["bonus_granted"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `payment webhook activates pro`() = testApplication {
        application { testModule(TestFixtures.freshModule()) }

        client.post("/api/v1/payments/tochka/create") {
            contentType(ContentType.Application.Json)
            setBody("""{"device_id":"$deviceId","tariff":"pro_monthly_199"}""")
        }

        val webhook = client.post("/api/v1/payments/tochka/webhook") {
            contentType(ContentType.Application.Json)
            header("X-Signature", "test-signature")
            setBody("""{"payment_id":"tochka_11111111","status":"paid"}""")
        }
        assertEquals(HttpStatusCode.OK, webhook.status)

        val status = client.get("/api/v1/subscription/status") {
            header("X-Device-Id", deviceId)
        }
        val body = json.parseToJsonElement(status.bodyAsText()).jsonObject
        assertTrue(body["is_pro"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `auth vk returns token`() = testApplication {
        application { testModule(TestFixtures.freshModule()) }

        val response = client.post("/api/v1/auth/vk") {
            contentType(ContentType.Application.Json)
            setBody("""{"device_id":"$deviceId","access_token":"vk_test_12345"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body["access_token"]!!.jsonPrimitive.content.isNotBlank())
        assertTrue(body["account_linked"]!!.jsonPrimitive.boolean)
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

    @Test
    fun `invalid device_id in bonus returns bad request`() = testApplication {
        application { testModule(TestFixtures.freshModule()) }
        val response = client.post("/api/v1/scan/bonus") {
            contentType(ContentType.Application.Json)
            setBody("""{"device_id":"not-a-uuid"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `diary without device_id returns bad request`() = testApplication {
        application { testModule(TestFixtures.freshModule()) }
        val today = LocalDate.now()
        val response = client.get("/api/v1/diary?date=$today")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
