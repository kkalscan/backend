package ru.kkalscan.domain.features

import ru.kkalscan.domain.port.FeatureSearchItemRecord

data class FeatureSearchQueryResult(
    val items: List<FeatureSearchItemRecord>,
    val popularFallback: Boolean,
)

/** Default catalog — mirrored in DB migration V5 and mobile fake API. */
object FeatureSearchCatalog {
    val items: List<FeatureSearchItemRecord> = listOf(
        FeatureSearchItemRecord("diary_today", "Сегодня", "Дневник питания и калории за день", "сегодня,дневник,калории,ккал,день,съедено,питание", "kkalscan://diary", "today", 10),
        FeatureSearchItemRecord("scan", "Сканировать еду", "Калории и БЖУ по фото", "скан,фото,камера,распознать,добавить,еда,сфотографировать", "kkalscan://scan", "scan", 5),
        FeatureSearchItemRecord("describe_food", "Описать еду", "Текстом — без выбора из базы продуктов", "описать,текст,написать,сказать,без фото,без базы,рассказать", "kkalscan://describe-food", "edit", 8),
        FeatureSearchItemRecord("food_search", "Найти продукт", "Добавить блюдо из каталога", "продукт,каталог,найти,борщ,творог,добавить еду", "kkalscan://food-search", "search", 15),
        FeatureSearchItemRecord("journal", "Дневник за неделю", "Графики калорий и БЖУ", "дневник,неделя,график,статистика,калории,прогресс,журнал", "kkalscan://journal", "journal", 20),
        FeatureSearchItemRecord("fiber", "Клетчатка", "График клетчатки за неделю", "клетчатка,график,кл,волокна,клетчатки", "kkalscan://journal/fiber", "fiber", 25),
        FeatureSearchItemRecord("profile", "Профиль", "Подписка Pro и настройки", "профиль,настройки,pro,подписка,аккаунт,личный", "kkalscan://profile", "profile", 30),
        FeatureSearchItemRecord("paywall", "Pro подписка", "Безлимитные сканы — 199 ₽/мес", "pro,подписка,безлимит,199,оплата,лимит", "kkalscan://paywall", "pro", 40),
        FeatureSearchItemRecord("macros", "БЖУ за неделю", "Белки, жиры и углеводы", "бжу,белки,жиры,углеводы,макросы,белок", "kkalscan://journal", "macros", 22),
        FeatureSearchItemRecord("free_scans", "Бесплатные сканы", "3 скана каждый день", "сканы,бесплатно,лимит,осталось,бесплатный", "kkalscan://diary", "gift", 12),
        FeatureSearchItemRecord("dietitian", "Анализ диетолога", "AI-разбор питания за неделю", "диетолог,анализ,разбор,ai,insight,рекомендации", "kkalscan://journal/dietitian", "dietitian", 35),
    )

    fun normalize(query: String): String =
        query.trim().lowercase().replace(Regex("\\s+"), " ")

    fun query(items: List<FeatureSearchItemRecord>, query: String, limit: Int): FeatureSearchQueryResult {
        val normalized = normalize(query)
        val safeLimit = limit.coerceIn(1, 50)
        if (normalized.isBlank()) {
            return FeatureSearchQueryResult(items = emptyList(), popularFallback = false)
        }
        val matched = items.mapNotNull { item ->
            score(item, normalized)?.let { item to it }
        }.sortedWith(compareByDescending<Pair<FeatureSearchItemRecord, Int>> { it.second }.thenBy { it.first.sortOrder })
            .map { it.first }
        if (matched.isNotEmpty()) {
            return FeatureSearchQueryResult(items = matched.take(safeLimit), popularFallback = false)
        }
        return FeatureSearchQueryResult(
            items = items.sortedBy { it.sortOrder }.take(safeLimit),
            popularFallback = true,
        )
    }

    fun search(items: List<FeatureSearchItemRecord>, query: String, limit: Int): List<FeatureSearchItemRecord> =
        query(items, query, limit).items

    private fun score(item: FeatureSearchItemRecord, normalized: String): Int? {
        val title = normalize(item.title)
        val subtitle = item.subtitle?.let { normalize(it) }.orEmpty()
        val keywords = item.keywords.split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() }
        if (title.contains(normalized) || subtitle.contains(normalized)) return 100
        val tokens = normalized.split(' ').filter { it.length >= 2 }
        if (tokens.isEmpty()) return null
        var score = 0
        for (token in tokens) {
            when {
                title.contains(token) -> score += 40
                subtitle.contains(token) -> score += 25
                keywords.any { it.contains(token) || token.contains(it) } -> score += 30
                else -> return null
            }
        }
        return score
    }
}
