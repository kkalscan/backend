package ru.kkalscan.domain.port

data class FeatureSearchItemRecord(
    val id: String,
    val title: String,
    val subtitle: String?,
    val keywords: String,
    val deeplink: String,
    val icon: String,
    val sortOrder: Int,
)

interface FeatureSearchRepository {
    suspend fun listEnabled(locale: String = "ru"): List<FeatureSearchItemRecord>
}
