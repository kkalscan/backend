package ru.kkalscan.domain.service

import ru.kkalscan.domain.BadRequestException

data class TariffOffer(
    val id: String,
    val title: String,
    val priceKopecks: Int,
    val durationDays: Long,
) {
    val priceRub: Int get() = priceKopecks / 100
}

object TariffCatalog {
    const val MONTHLY_ID = "pro_monthly_199"
    const val LIFETIME_ID = "pro_lifetime_5000"

    private val tariffs: Map<String, TariffOffer> = listOf(
        TariffOffer(
            id = MONTHLY_ID,
            title = "KkalScan Pro — месяц",
            priceKopecks = 20_000,
            durationDays = 30,
        ),
        TariffOffer(
            id = LIFETIME_ID,
            title = "KkalScan Pro — навсегда",
            priceKopecks = 500_000,
            durationDays = 36_500,
        ),
    ).associateBy { it.id }

    fun all(): List<TariffOffer> = tariffs.values.toList()

    fun require(tariffId: String): TariffOffer =
        tariffs[tariffId] ?: throw BadRequestException("Неизвестный тариф")

    fun find(tariffId: String): TariffOffer? = tariffs[tariffId]

    fun discountedKopecks(listKopecks: Int, discountPercent: Int): Int {
        val pct = discountPercent.coerceIn(0, 100)
        return listKopecks - (listKopecks * pct / 100)
    }
}
