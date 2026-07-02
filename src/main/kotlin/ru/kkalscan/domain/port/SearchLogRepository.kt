package ru.kkalscan.domain.port

import java.util.UUID

data class SearchLogRecord(
    val id: UUID,
    val deviceId: UUID,
    val query: String,
    val queryNormalized: String,
    val source: String,
    val resultsCount: Int,
)

data class SearchQueryStat(
    val query: String,
    val count: Int,
)

interface SearchLogRepository {
    suspend fun log(record: SearchLogRecord)
    suspend fun topQueries(days: Int, limit: Int): List<SearchQueryStat>
}
