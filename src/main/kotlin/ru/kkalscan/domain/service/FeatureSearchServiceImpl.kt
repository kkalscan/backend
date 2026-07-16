package ru.kkalscan.domain.service

import org.slf4j.LoggerFactory
import ru.kkalscan.AppConfig
import ru.kkalscan.domain.BadRequestException
import ru.kkalscan.domain.features.FeatureSearchCatalog
import ru.kkalscan.domain.port.FeatureSearchHit
import ru.kkalscan.domain.port.FeatureSearchIntentResult
import ru.kkalscan.domain.port.FeatureSearchRepository
import ru.kkalscan.domain.port.FeatureSearchResult
import ru.kkalscan.domain.port.FeatureSearchService
import ru.kkalscan.domain.port.SearchLogRecord
import ru.kkalscan.domain.port.SearchLogRepository
import ru.kkalscan.domain.port.VisionBudgetRepository
import ru.kkalscan.domain.port.VisionClient
import java.time.YearMonth
import java.util.UUID

class FeatureSearchServiceImpl(
    private val featureSearchRepository: FeatureSearchRepository,
    private val searchLogRepository: SearchLogRepository,
    private val visionClient: VisionClient,
    private val visionBudgetRepository: VisionBudgetRepository,
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

    override suspend fun classifyIntent(deviceId: UUID, query: String): FeatureSearchIntentResult {
        val trimmed = query.trim()
        if (trimmed.length < MIN_INTENT_QUERY_CHARS) {
            throw BadRequestException("Запрос слишком короткий")
        }
        if (trimmed.length > MAX_INTENT_QUERY_CHARS) {
            throw BadRequestException("Запрос слишком длинный")
        }

        val month = YearMonth.now()
        if (visionBudgetRepository.getMonthCost(month) >= AppConfig.visionMonthlyBudgetRub) {
            log.warn("feature_search_intent budget_exceeded device={}", mask(deviceId))
            return FeatureSearchIntentResult(query = trimmed, isFoodIntent = false)
        }

        val isFood = try {
            visionClient.classifySearchIntent(trimmed)
        } catch (e: Exception) {
            log.warn("feature_search_intent failed device={}: {}", mask(deviceId), e.message)
            return FeatureSearchIntentResult(query = trimmed, isFoodIntent = false)
        }

        visionBudgetRepository.addCost(month, AppConfig.visionCostPerIntentRub)
        log.info(
            "feature_search_intent device={} q={} isFood={}",
            mask(deviceId),
            FeatureSearchCatalog.normalize(trimmed),
            isFood,
        )
        return FeatureSearchIntentResult(query = trimmed, isFoodIntent = isFood)
    }

    private fun mask(deviceId: UUID): String = deviceId.toString().take(8) + "…"

    private companion object {
        const val MAX_LIMIT = 50
        const val MIN_INTENT_QUERY_CHARS = 3
        const val MAX_INTENT_QUERY_CHARS = 200
    }
}
