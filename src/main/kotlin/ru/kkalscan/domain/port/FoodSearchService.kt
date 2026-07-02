package ru.kkalscan.domain.port

import ru.kkalscan.domain.model.DishDto
import java.util.UUID

data class FoodSearchResult(
    val query: String,
    val items: List<DishDto>,
    val total: Int,
)

interface FoodSearchService {
    suspend fun search(deviceId: UUID, query: String, limit: Int, source: String): FoodSearchResult
    suspend fun topQueries(days: Int, limit: Int): List<SearchQueryStat>
}
