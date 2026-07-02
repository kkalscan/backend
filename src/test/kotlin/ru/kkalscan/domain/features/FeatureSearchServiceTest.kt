package ru.kkalscan.domain.features

import ru.kkalscan.domain.port.FeatureSearchItemRecord
import ru.kkalscan.domain.service.FeatureSearchServiceImpl
import ru.kkalscan.data.memory.InMemoryFeatureSearchRepository
import ru.kkalscan.data.memory.InMemorySearchLogRepository
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeatureSearchServiceTest {

    private val service = FeatureSearchServiceImpl(
        featureSearchRepository = InMemoryFeatureSearchRepository(),
        searchLogRepository = InMemorySearchLogRepository(),
    )

    @Test
    fun searchProfile_returnsProfileDeeplink() = runTest {
        val result = service.search(UUID.randomUUID(), "профиль", limit = 10, locale = "ru")
        assertTrue(result.items.any { it.deeplink == "kkalscan://profile" })
    }

    @Test
    fun emptyQuery_returnsEmpty() = runTest {
        val logs = InMemorySearchLogRepository()
        val service = FeatureSearchServiceImpl(
            featureSearchRepository = InMemoryFeatureSearchRepository(),
            searchLogRepository = logs,
        )
        val result = service.search(UUID.randomUUID(), "", limit = 5, locale = "ru")
        assertTrue(result.items.isEmpty())
        assertEquals(1, logs.all().size)
        assertEquals(0, logs.all().first().resultsCount)
    }

    @Test
    fun unknownQuery_returnsPopularFallback() = runTest {
        val logs = InMemorySearchLogRepository()
        val service = FeatureSearchServiceImpl(
            featureSearchRepository = InMemoryFeatureSearchRepository(),
            searchLogRepository = logs,
        )
        val result = service.search(UUID.randomUUID(), "xyzunknown123", limit = 5, locale = "ru")
        assertTrue(result.items.isNotEmpty())
        assertTrue(result.popularFallback)
        assertEquals(1, logs.all().size)
        assertEquals(0, logs.all().first().resultsCount)
    }

    @Test
    fun searchProfile_logsQuery() = runTest {
        val logs = InMemorySearchLogRepository()
        val service = FeatureSearchServiceImpl(
            featureSearchRepository = InMemoryFeatureSearchRepository(),
            searchLogRepository = logs,
        )
        service.search(UUID.randomUUID(), "профиль", limit = 10, locale = "ru")
        assertEquals(1, logs.all().size)
        assertEquals("профиль", logs.all().first().queryNormalized)
    }

    @Test
    fun catalogSearch_matchesKeywords() {
        val items = listOf(
            FeatureSearchItemRecord("a", "Test", "Sub", "ключ", "kkalscan://diary", "today", 1),
        )
        val matched = FeatureSearchCatalog.search(items, "ключ", limit = 10)
        assertEquals(1, matched.size)
    }
}
