package ru.kkalscan.domain.service

import ru.kkalscan.domain.BadRequestException
import ru.kkalscan.domain.port.DevicePromoBindingRepository
import ru.kkalscan.domain.port.PromoCode
import ru.kkalscan.domain.port.PromoCodeRepository
import java.util.UUID

class PromoService(
    private val promoCodes: PromoCodeRepository,
    private val bindings: DevicePromoBindingRepository,
) {
    data class ApplyResult(
        val promoCode: String,
        val discountPercent: Int,
    )

    data class BoundPromo(
        val promoCode: String,
        val discountPercent: Int,
    )

    data class SubscriptionOffer(
        val tariff: String,
        val title: String,
        val priceRub: Int,
        val amountRub: Int,
        val amountKopecks: Int,
        val discountPercent: Int,
        val promoCode: String?,
    )

    fun applyPromo(deviceId: UUID, code: String): ApplyResult {
        val promo = requireActive(code)
        bindings.bind(deviceId, promo.code)
        return ApplyResult(promoCode = promo.code, discountPercent = promo.discountPercent)
    }

    fun getBoundPromo(deviceId: UUID): BoundPromo? {
        val code = bindings.getBoundCode(deviceId) ?: return null
        val promo = promoCodes.findActive(code) ?: return null
        return BoundPromo(promoCode = promo.code, discountPercent = promo.discountPercent)
    }

    fun listOffers(deviceId: UUID): List<SubscriptionOffer> {
        val bound = getBoundPromo(deviceId)
        val discount = bound?.discountPercent ?: 0
        return TariffCatalog.all().map { tariff ->
            val amountKopecks = TariffCatalog.discountedKopecks(tariff.priceKopecks, discount)
            SubscriptionOffer(
                tariff = tariff.id,
                title = tariff.title,
                priceRub = tariff.priceRub,
                amountRub = amountKopecks / 100,
                amountKopecks = amountKopecks,
                discountPercent = discount,
                promoCode = bound?.promoCode?.takeIf { discount > 0 },
            )
        }
    }

    fun requireActive(code: String): PromoCode {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) throw BadRequestException("Неверный промокод")
        return promoCodes.findActive(trimmed)
            ?: throw BadRequestException("Неверный промокод")
    }
}
