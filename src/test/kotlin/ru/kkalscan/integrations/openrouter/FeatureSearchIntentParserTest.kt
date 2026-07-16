package ru.kkalscan.integrations.openrouter

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureSearchIntentParserTest {
    @Test
    fun parse_trueJson() {
        assertTrue(FeatureSearchIntentParser.parse("""{"isFoodIntent":true}"""))
    }

    @Test
    fun parse_falseJson() {
        assertFalse(FeatureSearchIntentParser.parse("""{"isFoodIntent":false}"""))
    }

    @Test
    fun parse_fencedMarkdown() {
        assertTrue(FeatureSearchIntentParser.parse("```json\n{\"isFoodIntent\": true}\n```"))
    }

    @Test
    fun parse_garbage_defaultsFalse() {
        assertFalse(FeatureSearchIntentParser.parse("not json"))
    }
}
