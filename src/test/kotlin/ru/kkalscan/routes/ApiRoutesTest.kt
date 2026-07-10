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
import kotlinx.serialization.json.double
import ru.kkalscan.AppConfig
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
        val privacy = client.get("/privacy").bodyAsText()
        assertTrue(privacy.contains("политика конфиденциальности"))
        assertTrue(privacy.contains("device_id"))
        val pay = client.get("/pay?device_id=$deviceId")
        assertEquals(HttpStatusCode.OK, pay.status)
        val payHtml = pay.bodyAsText()
        assertTrue(payHtml.contains("KkalScan Pro") || payHtml.contains("Спасибо"))
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
    fun `scan text and diary flow`() = testApplication {
        application { testModule(TestFixtures.freshModule()) }

        val scanResponse = client.post("/api/v1/scan/text") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "device_id": "$deviceId",
                  "description": "тарелка борща и кусок хлеба"
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.OK, scanResponse.status)
        val scanBody = json.parseToJsonElement(scanResponse.bodyAsText()).jsonObject
        val scanId = scanBody["scan_id"]!!.jsonPrimitive.content
        assertTrue(scanBody["dishes"]!!.jsonArray.isNotEmpty())
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
        val entry = diaryBody["entry"]!!.jsonObject
        assertTrue(entry["dishes"]!!.jsonArray.isNotEmpty())

        val today = LocalDate.now().toString()
        val dayResponse = client.get("/api/v1/diary?device_id=$deviceId&date=$today&timezone_offset_minutes=180")
        assertEquals(HttpStatusCode.OK, dayResponse.status)
        val dayBody = json.parseToJsonElement(dayResponse.bodyAsText()).jsonObject
        assertEquals(1, dayBody["entries"]!!.jsonArray.size)
        assertTrue(dayBody["total_kcal"]!!.jsonPrimitive.int > 0)
    }

    @Test
    fun `activity emulator population default`() = testApplication {
        application { testModule(TestFixtures.freshModule()) }
        val response = client.get("/api/v1/activity/emulator?device_id=$deviceId&timezone_offset_minutes=180")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("population_default", body["mode"]!!.jsonPrimitive.content)
        val activeKcal = body["estimated_active_kcal"]!!.jsonPrimitive.int
        assertTrue(activeKcal in 0..1500)
        assertEquals((activeKcal / 0.04).toInt(), body["estimated_steps"]!!.jsonPrimitive.int)
    }

    @Test
    fun `activity emulator diary based after burn history`() = testApplication {
        application { testModule(TestFixtures.freshModule()) }
        client.post("/api/v1/diary/workouts") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "device_id": "$deviceId",
                  "name": "Бег",
                  "kcal": 500
                }
                """.trimIndent(),
            )
        }
        client.put("/api/v1/diary/activity") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "device_id": "$deviceId",
                  "steps": 10000,
                  "kcal": 400,
                  "source": "device_sensor",
                  "timezone_offset_minutes": 180
                }
                """.trimIndent(),
            )
        }
        val response = client.get("/api/v1/activity/emulator?device_id=$deviceId&timezone_offset_minutes=180")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("diary_based", body["mode"]!!.jsonPrimitive.content)
        assertEquals(900, body["avg_consumed_kcal_per_day"]!!.jsonPrimitive.int)
        val activeKcal = body["estimated_active_kcal"]!!.jsonPrimitive.int
        assertTrue(activeKcal in 0..900)
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
                    {"name": "Овсянка", "grams": 200, "kcal": 150, "protein": 5.0, "fat": 3.0, "carbs": 27.0, "fiber": 4.2}
                  ]
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(150, body["entry"]!!.jsonObject["total_kcal"]!!.jsonPrimitive.int)
        val dish = body["entry"]!!.jsonObject["dishes"]!!.jsonArray.single().jsonObject
        assertEquals(4.2, dish["fiber"]!!.jsonPrimitive.double)
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
    fun `workout text parse returns ai estimate`() = testApplication {
        application { testModule(TestFixtures.freshModule()) }

        val response = client.post("/api/v1/workout/text") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "device_id": "$deviceId",
                  "description": "бег 30 минут"
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("Бег", body["title"]!!.jsonPrimitive.content)
        assertEquals(300, body["burned_kcal"]!!.jsonPrimitive.int)
        assertEquals(30, body["duration_minutes"]!!.jsonPrimitive.int)
    }

    @Test
    fun `workout create updates diary balance`() = testApplication {
        application { testModule(TestFixtures.freshModule()) }
        val today = LocalDate.now().toString()

        client.post("/api/v1/diary/entries") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "device_id": "$deviceId",
                  "meal_type": "lunch",
                  "dishes": [
                    {"name": "Суп", "grams": 300, "kcal": 400, "protein": 10.0, "fat": 5.0, "carbs": 40.0}
                  ]
                }
                """.trimIndent(),
            )
        }

        val createWorkout = client.post("/api/v1/diary/workouts") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "device_id": "$deviceId",
                  "name": "Бег",
                  "kcal": 150
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.Created, createWorkout.status)
        val workoutBody = json.parseToJsonElement(createWorkout.bodyAsText()).jsonObject
        val workoutId = workoutBody["workout"]!!.jsonObject["id"]!!.jsonPrimitive.content
        assertEquals("Бег", workoutBody["workout"]!!.jsonObject["name"]!!.jsonPrimitive.content)

        val day = client.get("/api/v1/diary?device_id=$deviceId&date=$today&timezone_offset_minutes=180")
        assertEquals(HttpStatusCode.OK, day.status)
        val dayBody = json.parseToJsonElement(day.bodyAsText()).jsonObject
        assertEquals(400, dayBody["total_kcal"]!!.jsonPrimitive.int)
        assertEquals(150, dayBody["total_burned_kcal"]!!.jsonPrimitive.int)
        assertEquals(250, dayBody["net_kcal"]!!.jsonPrimitive.int)
        assertEquals(1, dayBody["workouts"]!!.jsonArray.size)

        val deleteWorkout = client.delete("/api/v1/diary/workouts/$workoutId") {
            header("X-Device-Id", deviceId)
        }
        assertEquals(HttpStatusCode.NoContent, deleteWorkout.status)

        val dayAfterDelete = client.get("/api/v1/diary?device_id=$deviceId&date=$today&timezone_offset_minutes=180")
        val afterBody = json.parseToJsonElement(dayAfterDelete.bodyAsText()).jsonObject
        assertEquals(0, afterBody["total_burned_kcal"]!!.jsonPrimitive.int)
        assertEquals(400, afterBody["net_kcal"]!!.jsonPrimitive.int)
        assertEquals(0, afterBody["workouts"]!!.jsonArray.size)
    }

    @Test
    fun `activity sync persists steps and sums with workouts`() = testApplication {
        application { testModule(TestFixtures.freshModule()) }
        val deviceId = TestFixtures.deviceId.toString()
        val today = java.time.LocalDate.now().toString()

        client.post("/api/v1/diary/workouts") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "device_id": "$deviceId",
                  "name": "Бег",
                  "kcal": 120
                }
                """.trimIndent(),
            )
        }

        val sync = client.put("/api/v1/diary/activity") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "device_id": "$deviceId",
                  "steps": 5600,
                  "kcal": 224,
                  "source": "device_sensor",
                  "timezone_offset_minutes": 180
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.OK, sync.status)
        val body = json.parseToJsonElement(sync.bodyAsText()).jsonObject
        assertEquals(224, body["activity_kcal"]!!.jsonPrimitive.int)
        assertEquals(5600, body["activity_steps"]!!.jsonPrimitive.int)
        assertEquals(344, body["total_burned_kcal"]!!.jsonPrimitive.int)
        assertEquals("device_sensor", body["activity_source"]!!.jsonPrimitive.content)

        val day = client.get("/api/v1/diary?device_id=$deviceId&date=$today&timezone_offset_minutes=180")
        val dayBody = json.parseToJsonElement(day.bodyAsText()).jsonObject
        assertEquals(344, dayBody["total_burned_kcal"]!!.jsonPrimitive.int)
    }

    @Test
    fun `workout validation rejects short name`() = testApplication {
        application { testModule(TestFixtures.freshModule()) }

        val response = client.post("/api/v1/diary/workouts") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "device_id": "$deviceId",
                  "name": "Б",
                  "kcal": 100
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
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
    fun `pro start activates pro without payment when free mode enabled`() = testApplication {
        application { testModule(TestFixtures.freshModule()) }

        val response = client.post("/api/v1/payments/pro/start") {
            contentType(ContentType.Application.Json)
            setBody("""{"device_id":"$deviceId","tariff":"pro_monthly_199"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body["is_pro"]!!.jsonPrimitive.boolean)
        assertEquals(false, body["payment_required"]!!.jsonPrimitive.boolean)

        val status = client.get("/api/v1/subscription/status") {
            header("X-Device-Id", deviceId)
        }
        assertTrue(
            json.parseToJsonElement(status.bodyAsText()).jsonObject["is_pro"]!!.jsonPrimitive.boolean,
        )
    }

    @Test
    fun `test payment activates pro and sends email`() = testApplication {
        application { testModule(TestFixtures.freshModule()) }

        val response = client.post("/api/v1/payments/test/activate") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"device_id":"$deviceId","secret":"${AppConfig.testPaymentSecret}"}""",
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body["is_pro"]!!.jsonPrimitive.boolean)
        assertTrue(body["email_sent"]!!.jsonPrimitive.boolean)
        assertEquals("pro_monthly_199", body["tariff"]!!.jsonPrimitive.content)

        val status = client.get("/api/v1/subscription/status") {
            header("X-Device-Id", deviceId)
        }
        assertTrue(
            json.parseToJsonElement(status.bodyAsText()).jsonObject["is_pro"]!!.jsonPrimitive.boolean,
        )
    }

    @Test
    fun `payment webhook activates pro`() = testApplication {
        application { testModule(TestFixtures.freshModule()) }

        val create = client.post("/api/v1/payments/tochka/create") {
            contentType(ContentType.Application.Json)
            setBody("""{"device_id":"$deviceId","tariff":"pro_monthly_199"}""")
        }
        val paymentId = json.parseToJsonElement(create.bodyAsText()).jsonObject["payment_id"]!!.jsonPrimitive.content
        val tochkaId = "tochka_${paymentId.take(8)}"

        val webhook = client.post("/api/v1/payments/tochka/webhook") {
            contentType(ContentType.Application.Json)
            header("X-Signature", "test-signature")
            setBody("""{"payment_id":"$tochkaId","payment_link_id":"$paymentId","status":"paid"}""")
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

    @Test
    fun `bug report grants pro once per device`() = testApplication {
        application { testModule(TestFixtures.freshModule()) }

        val first = client.post("/api/v1/feedback/bug") {
            header("X-Device-Id", deviceId)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("email", "bug@example.com")
                        append("description", "Кнопка скана не открывает камеру на Android 14")
                    },
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, first.status)
        val firstBody = json.parseToJsonElement(first.bodyAsText()).jsonObject
        assertTrue(firstBody["is_pro"]!!.jsonPrimitive.boolean)

        val status = client.get("/api/v1/subscription/status") {
            header("X-Device-Id", deviceId)
        }
        assertTrue(
            json.parseToJsonElement(status.bodyAsText()).jsonObject["is_pro"]!!.jsonPrimitive.boolean,
        )

        val second = client.post("/api/v1/feedback/bug") {
            header("X-Device-Id", deviceId)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("email", "other@example.com")
                        append("description", "Второй баг-репорт с достаточным описанием")
                    },
                ),
            )
        }
        assertEquals(HttpStatusCode.Conflict, second.status)
    }

    @Test
    fun `food search logs queries and returns results`() = testApplication {
        val module = TestFixtures.freshModule()
        application { testModule(module) }

        val search = client.get("/api/v1/food/search?q=борщ&source=diary") {
            header("X-Device-Id", deviceId)
        }
        assertEquals(HttpStatusCode.OK, search.status)
        val body = json.parseToJsonElement(search.bodyAsText()).jsonObject
        assertTrue(body["items"]!!.jsonArray.isNotEmpty())
        assertEquals("борщ", body["query"]!!.jsonPrimitive.content)

        val empty = client.get("/api/v1/food/search?q=xyzunknown123&source=diary") {
            header("X-Device-Id", deviceId)
        }
        assertEquals(HttpStatusCode.OK, empty.status)
        assertTrue(json.parseToJsonElement(empty.bodyAsText()).jsonObject["items"]!!.jsonArray.isEmpty())

        val top = client.get("/api/v1/analytics/search-top?days=30&limit=10")
        assertEquals(HttpStatusCode.OK, top.status)
        val queries = json.parseToJsonElement(top.bodyAsText()).jsonObject["queries"]!!.jsonArray
        assertTrue(queries.any { it.jsonObject["query"]!!.jsonPrimitive.content == "борщ" })
        assertTrue(queries.any { it.jsonObject["query"]!!.jsonPrimitive.content == "xyzunknown123" })
    }

    @Test
    fun `feature search returns deeplinks and logs queries`() = testApplication {
        val module = TestFixtures.freshModule()
        application { testModule(module) }

        val popular = client.get("/api/v1/features/search") {
            header("X-Device-Id", deviceId)
        }
        assertEquals(HttpStatusCode.OK, popular.status)
        val popularBody = json.parseToJsonElement(popular.bodyAsText()).jsonObject
        assertEquals(0, popularBody["items"]!!.jsonArray.size)

        val unknown = client.get("/api/v1/features/search?q=xyzunknown123") {
            header("X-Device-Id", deviceId)
        }
        assertEquals(HttpStatusCode.OK, unknown.status)
        val unknownBody = json.parseToJsonElement(unknown.bodyAsText()).jsonObject
        assertTrue(unknownBody["items"]!!.jsonArray.size >= 3)
        assertTrue(unknownBody["popular_fallback"]!!.jsonPrimitive.boolean)

        val profile = client.get("/api/v1/features/search?q=профиль") {
            header("X-Device-Id", deviceId)
        }
        assertEquals(HttpStatusCode.OK, profile.status)
        val profileBody = json.parseToJsonElement(profile.bodyAsText()).jsonObject
        assertTrue(
            profileBody["items"]!!.jsonArray.any {
                it.jsonObject["deeplink"]!!.jsonPrimitive.content == "kkalscan://profile"
            },
        )

        val top = client.get("/api/v1/analytics/search-top?days=30&limit=20")
        assertEquals(HttpStatusCode.OK, top.status)
        val queries = json.parseToJsonElement(top.bodyAsText()).jsonObject["queries"]!!.jsonArray
        assertTrue(queries.any { it.jsonObject["query"]!!.jsonPrimitive.content == "профиль" })
    }
}
