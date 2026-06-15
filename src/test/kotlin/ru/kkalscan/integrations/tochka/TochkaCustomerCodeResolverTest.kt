package ru.kkalscan.integrations.tochka

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TochkaCustomerCodeResolverTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val resolver = TochkaCustomerCodeResolver(
        httpClient = io.ktor.client.HttpClient(),
        accessToken = "test",
        envOverride = "",
    )

    @Test
    fun `picks Business customer from list`() {
        val root = json.parseToJsonElement(
            """
            {
              "Data": {
                "Customer": [
                  {"customerCode": "111", "customerType": "Personal"},
                  {"customerCode": "300000092", "customerType": "Business"}
                ]
              }
            }
            """.trimIndent(),
        ).jsonObject

        assertEquals("300000092", resolver.parseCustomerCode(root))
    }

    @Test
    fun `uses single customer when only one entry`() {
        val root = json.parseToJsonElement(
            """
            {
              "Data": {
                "Customer": [
                  {"customerCode": "300000092", "customerType": "Personal"}
                ]
              }
            }
            """.trimIndent(),
        ).jsonObject

        assertEquals("300000092", resolver.parseCustomerCode(root))
    }

    @Test
    fun `reads customerCode from flat Data object`() {
        val root = buildJsonObject {
            put(
                "Data",
                buildJsonObject {
                    put("customerCode", "300000092")
                    put("customerType", "Business")
                },
            )
        }

        assertEquals("300000092", resolver.parseCustomerCode(root))
    }

    @Test
    fun `fails when multiple customers and no Business`() {
        val root = json.parseToJsonElement(
            """
            {
              "Data": {
                "Customer": [
                  {"customerCode": "111", "customerType": "Personal"},
                  {"customerCode": "222", "customerType": "Personal"}
                ]
              }
            }
            """.trimIndent(),
        ).jsonObject

        assertFailsWith<ru.kkalscan.domain.BadRequestException> {
            resolver.parseCustomerCode(root)
        }
    }
}
