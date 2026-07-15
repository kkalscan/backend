package ru.kkalscan.domain.port

import java.time.Instant
import java.util.UUID

data class PromoPurchaseRecord(
    val id: UUID,
    val paymentId: UUID,
    val deviceId: UUID,
    val tariff: String,
    val amountKopecks: Int,
    val listAmountKopecks: Int,
    val promoCode: String?,
    val discountPercent: Int,
    val status: String,
    val paidAt: Instant,
)

interface PromoPurchaseRepository {
    suspend fun record(record: PromoPurchaseRecord)

    suspend fun findById(id: UUID): PromoPurchaseRecord?
}
