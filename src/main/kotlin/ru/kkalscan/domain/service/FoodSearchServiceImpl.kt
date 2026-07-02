package ru.kkalscan.domain.service

import org.slf4j.LoggerFactory
import ru.kkalscan.domain.food.FoodCatalog
import ru.kkalscan.domain.port.FoodSearchResult
import ru.kkalscan.domain.port.FoodSearchService
import ru.kkalscan.domain.port.SearchLogRecord
import ru.kkalscan.domain.port.SearchLogRepository
import ru.kkalscan.domain.port.SearchQueryStat
import java.util.UUID

class FoodSearchServiceImpl(
    private val searchLogRepository: SearchLogRepository,
) : FoodSearchService {

    private val log = LoggerFactory.getLogger(FoodSearchServiceImpl::class.java)

    override suspend fun search(deviceId: UUID, query: String, limit: Int, source: String): FoodSearchResult {
        val trimmed = query.trim()
        val normalized = FoodCatalog.normalize(trimmed)
        val safeLimit = limit.coerceIn(1, MAX_LIMIT)
        val items = FoodCatalog.search(trimmed, safeLimit)

        if (normalized.isNotBlank()) {
            searchLogRepository.log(
                SearchLogRecord(
                    id = UUID.randomUUID(),
                    deviceId = deviceId,
                    query = trimmed,
                    queryNormalized = normalized,
                    source = source.ifBlank { "diary" },
                    resultsCount = items.size,
                ),
            )
            log.info(
                "food_search device={} q={} results={} source={}",
                mask(deviceId),
                normalized,
                items.size,
                source,
            )
        }

        return FoodSearchResult(
            query = trimmed,
            items = items,
            total = items.size,
        )
    }

    override suspend fun topQueries(days: Int, limit: Int): List<SearchQueryStat> =
        searchLogRepository.topQueries(days.coerceIn(1, 365), limit.coerceIn(1, 200))

    private fun mask(deviceId: UUID): String = deviceId.toString().take(8) + "…"

    private companion object {
        const val MAX_LIMIT = 50
    }
}
