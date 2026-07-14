package ru.kkalscan.domain.port

import java.util.UUID

data class PromoCode(
    val code: String,
    val discountPercent: Int,
    val active: Boolean = true,
)

interface PromoCodeRepository {
    fun findActive(code: String): PromoCode?
    fun upsert(promo: PromoCode)
}

interface DevicePromoBindingRepository {
    fun getBoundCode(deviceId: UUID): String?
    fun bind(deviceId: UUID, promoCode: String)
}
