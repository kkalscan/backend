package ru.kkalscan.integrations.openrouter

import kotlin.test.Test
import kotlin.test.assertEquals

class VisionResponseParserTest {
    @Test
    fun `parses plain json`() {
        val dishes = VisionResponseParser.parse(
            """{"dishes":[{"name":"Борщ","grams":300,"kcal":180,"protein":8.5,"fat":6.2,"carbs":22.1,"fiber":4.4}]}""",
        )
        assertEquals("Борщ", dishes.single().name)
        assertEquals(180, dishes.single().kcal)
        assertEquals(4.4, dishes.single().fiber)
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

    @Test
    fun `coerces float kcal and grams to int`() {
        val dishes = VisionResponseParser.parse(
            """{"dishes":[{"name":"Нектарин","grams":320.0,"kcal":136.5,"protein":3.52,"fat":0.96,"carbs":32.32,"fiber":5.12}]}""",
        )
        assertEquals("Нектарин", dishes.single().name)
        assertEquals(320, dishes.single().grams)
        assertEquals(137, dishes.single().kcal)
    }
}
