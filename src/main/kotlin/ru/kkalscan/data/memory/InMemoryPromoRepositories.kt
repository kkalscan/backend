package ru.kkalscan.data.memory

import ru.kkalscan.domain.port.DevicePromoBindingRepository
import ru.kkalscan.domain.port.PromoCode
import ru.kkalscan.domain.port.PromoCodeRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryPromoCodeRepository(
    seed: List<PromoCode> = listOf(
        PromoCode(code = "Lida", discountPercent = 50, active = true),
    ),
) : PromoCodeRepository {
    private val byKey = ConcurrentHashMap<String, PromoCode>()

    init {
        seed.forEach { upsert(it) }
    }

    override fun findActive(code: String): PromoCode? {
        val key = normalize(code)
        val promo = byKey[key] ?: return null
        return promo.takeIf { it.active }
    }

    override fun upsert(promo: PromoCode) {
        byKey[normalize(promo.code)] = promo.copy(code = promo.code.trim())
    }

    private fun normalize(code: String): String = code.trim().lowercase()
}

class InMemoryDevicePromoBindingRepository : DevicePromoBindingRepository {
    private val bindings = ConcurrentHashMap<UUID, String>()

    override fun getBoundCode(deviceId: UUID): String? = bindings[deviceId]

    override fun bind(deviceId: UUID, promoCode: String) {
        bindings[deviceId] = promoCode.trim()
    }
}
