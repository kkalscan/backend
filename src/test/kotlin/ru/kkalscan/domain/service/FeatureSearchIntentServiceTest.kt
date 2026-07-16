package ru.kkalscan.domain.service

import kotlinx.coroutines.test.runTest
import ru.kkalscan.data.memory.InMemoryFeatureSearchRepository
import ru.kkalscan.data.memory.InMemorySearchLogRepository
import ru.kkalscan.data.memory.InMemoryVisionBudgetRepository
import ru.kkalscan.domain.BadRequestException
import ru.kkalscan.integrations.StubVisionClient
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureSearchIntentServiceTest {

    private fun service(
        visionBudget: InMemoryVisionBudgetRepository = InMemoryVisionBudgetRepository(),
    ) = FeatureSearchServiceImpl(
        featureSearchRepository = InMemoryFeatureSearchRepository(),
        searchLogRepository = InMemorySearchLogRepository(),
        visionClient = StubVisionClient(),
        visionBudgetRepository = visionBudget,
    )

    @Test
    fun shortQuery_badRequest() = runTest {
        assertFailsWith<BadRequestException> {
            service().classifyIntent(UUID.randomUUID(), "ab")
        }
    }

    @Test
    fun foodQuery_true() = runTest {
        val result = service().classifyIntent(UUID.randomUUID(), "борщ")
        assertEquals("борщ", result.query)
        assertTrue(result.isFoodIntent)
    }

    @Test
    fun featureQuery_false() = runTest {
        val result = service().classifyIntent(UUID.randomUUID(), "профиль")
        assertFalse(result.isFoodIntent)
    }
}
