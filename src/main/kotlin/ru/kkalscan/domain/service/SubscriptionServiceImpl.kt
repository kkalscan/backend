package ru.kkalscan.domain.service

import ru.kkalscan.domain.model.Actor
import ru.kkalscan.domain.port.DeviceRepository
import ru.kkalscan.domain.port.SubscriptionService
import ru.kkalscan.domain.port.UserRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class SubscriptionServiceImpl(
    private val deviceRepository: DeviceRepository,
    private val userRepository: UserRepository,
) : SubscriptionService {

    override suspend fun getStatus(actor: Actor): SubscriptionService.SubscriptionStatus {
        val device = deviceRepository.findById(actor.deviceId)
        val user = actor.userId?.let { userRepository.findById(it) }
        val proUntil = user?.proUntil ?: device?.proUntil
        val isPro = proUntil != null && proUntil.isAfter(Instant.now())

        return SubscriptionService.SubscriptionStatus(
            isPro = isPro,
            proUntil = proUntil,
            accountLinked = actor.accountLinked,
            linkedProviders = actor.linkedProviders,
            tariff = if (isPro) TARIFF else null,
        )
    }

    override suspend fun activatePro(deviceId: UUID, tariff: String, paidAt: Instant) {
        val until = paidAt.plus(PRO_DAYS, ChronoUnit.DAYS)
        val device = deviceRepository.getOrCreate(deviceId)
        if (device.userId != null) {
            val user = userRepository.findById(device.userId)!!
            val newUntil = maxInstant(user.proUntil, until)
            userRepository.setProUntil(device.userId, newUntil)
        } else {
            val newUntil = maxInstant(device.proUntil, until)
            deviceRepository.setProUntil(deviceId, newUntil)
        }
    }

    private fun maxInstant(a: Instant?, b: Instant): Instant =
        when {
            a == null -> b
            a.isAfter(b) -> a
            else -> b
        }

    companion object {
        const val TARIFF = "pro_monthly_199"
        const val PRO_DAYS = 30L
    }
}
