package ru.kkalscan.integrations.openrouter

import kotlin.test.Test
import kotlin.test.assertEquals

class VisionResponseParserTest {
    @Test
    fun `parses plain json`() {
        val dishes = VisionResponseParser.parse(
            """{"dishes":[{"name":"Борщ","grams":300,"kcal":180,"protein":8.5,"fat":6.2,"carbs":22.1}]}""",
        )
        assertEquals("Борщ", dishes.single().name)
        assertEquals(180, dishes.single().kcal)
    }

    @Test
    fun `parses markdown fenced json`() {
        val dishes = VisionResponseParser.parse(
            """
            ```json
            {"dishes":[{"name":"Овсянка","grams":200,"kcal":320,"protein":12,"fat":8,"carbs":52}]}
            ```
            """.trimIndent(),
        )
        assertEquals("Овсянка", dishes.single().name)
    }
}
