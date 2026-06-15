package ru.kkalscan

import kotlin.test.Test
import kotlin.test.assertEquals

class AppConfigTest {
    @Test
    fun normalizeOpenRouterModel_migratesRemovedGemini20() {
        assertEquals("google/gemini-2.5-flash", normalizeOpenRouterModel("google/gemini-2.0-flash-001"))
    }

    @Test
    fun normalizeOpenRouterModel_keepsCustomModel() {
        assertEquals("openai/gpt-4o-mini", normalizeOpenRouterModel("openai/gpt-4o-mini"))
    }
}
