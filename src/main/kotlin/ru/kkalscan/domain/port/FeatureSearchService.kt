package ru.kkalscan.domain.port

import java.util.UUID

data class FeatureSearchHit(
    val id: String,
    val title: String,
    val subtitle: String?,
    val deeplink: String,
    val icon: String,
)

data class FeatureSearchResult(
    val query: String,
    val items: List<FeatureSearchHit>,
    val total: Int,
    val popularFallback: Boolean = false,
)

interface FeatureSearchService {
    suspend fun search(deviceId: UUID, query: String, limit: Int, locale: String = "ru"): FeatureSearchResult
}
