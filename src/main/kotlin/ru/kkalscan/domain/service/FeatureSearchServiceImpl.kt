package ru.kkalscan.domain.service

import org.slf4j.LoggerFactory
import ru.kkalscan.domain.features.FeatureSearchCatalog
import ru.kkalscan.domain.port.FeatureSearchHit
import ru.kkalscan.domain.port.FeatureSearchRepository
import ru.kkalscan.domain.port.FeatureSearchResult
import ru.kkalscan.domain.port.FeatureSearchService
import ru.kkalscan.domain.port.SearchLogRecord
import ru.kkalscan.domain.port.SearchLogRepository
import java.util.UUID

class FeatureSearchServiceImpl(
    private val featureSearchRepository: FeatureSearchRepository,
    private val searchLogRepository: SearchLogRepository,
) : FeatureSearchService {

    private val log = LoggerFactory.getLogger(FeatureSearchServiceImpl::class.java)

    override suspend fun search(deviceId: UUID, query: String, limit: Int, locale: String): FeatureSearchResult {
        val trimmed = query.trim()
        val normalized = FeatureSearchCatalog.normalize(trimmed)
        val safeLimit = limit.coerceIn(1, MAX_LIMIT)
        val catalog = featureSearchRepository.listEnabled(locale)
        val outcome = FeatureSearchCatalog.query(catalog, trimmed, safeLimit)
        val matchedCount = if (outcome.popularFallback) 0 else outcome.items.size

        searchLogRepository.log(
            SearchLogRecord(
                id = UUID.randomUUID(),
                deviceId = deviceId,
                query = trimmed,
                queryNormalized = normalized,
                source = "features",
                resultsCount = matchedCount,
            ),
        )
        log.info(
            "feature_search device={} q={} results={} popular={}",
            mask(deviceId),
            normalized.ifBlank { "(empty)" },
            matchedCount,
            outcome.popularFallback,
        )

        return FeatureSearchResult(
            query = trimmed,
            items = outcome.items.map { item ->
                FeatureSearchHit(
                    id = item.id,
                    title = item.title,
                    subtitle = item.subtitle,
                    deeplink = item.deeplink,
                    icon = item.icon,
                )
            },
            total = outcome.items.size,
            popularFallback = outcome.popularFallback,
        )
    }

    private fun mask(deviceId: UUID): String = deviceId.toString().take(8) + "…"

    private companion object {
        const val MAX_LIMIT = 50
    }
}
