package ru.kkalscan.domain.service

import ru.kkalscan.AppConfig
import ru.kkalscan.domain.BonusAlreadyUsedException
import ru.kkalscan.domain.LimitHitException
import ru.kkalscan.domain.model.Actor
import ru.kkalscan.domain.port.DeviceRepository
import ru.kkalscan.domain.port.ScanQuotaRepository
import ru.kkalscan.domain.port.UserRepository
import ru.kkalscan.domain.port.QuotaService
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

object QuotaRules {
    const val FREE_SCANS_PER_DAY = 3
    const val AD_BONUS_SCANS = 2

    fun allowance(quota: ru.kkalscan.domain.port.ScanQuotaRecord): Int =
        FREE_SCANS_PER_DAY + if (quota.bonusGranted) quota.bonusScans else 0

    fun scansLeft(quota: ru.kkalscan.domain.port.ScanQuotaRecord): Int =
        (allowance(quota) - quota.scansUsed).coerceAtLeast(0)
}

class QuotaServiceImpl(
    private val quotaRepository: ScanQuotaRepository,
    private val deviceRepository: DeviceRepository,
    private val userRepository: UserRepository,
) : QuotaService {

    override suspend fun canStartScan(actor: Actor, localDate: LocalDate): Boolean {
        if (actor.isPro) return true
        return getScansLeft(actor, localDate)?.let { it > 0 } == true
    }

    override suspend fun consumeScan(actor: Actor, localDate: LocalDate, scanSessionId: UUID?): Int? {
        if (actor.isPro) return null
        val left = getScansLeft(actor, localDate) ?: 0
        if (left <= 0) throw LimitHitException(0)
        quotaRepository.incrementUsed(actor.deviceId, localDate)
        return getScansLeft(actor, localDate)
    }

    override suspend fun grantAdBonus(actor: Actor, localDate: LocalDate): QuotaService.BonusResult {
        val quota = quotaRepository.getOrCreate(actor.deviceId, localDate)
        if (quota.bonusGranted) throw BonusAlreadyUsedException()
        quotaRepository.grantBonus(actor.deviceId, localDate)
        val left = getScansLeft(actor, localDate) ?: 0
        return QuotaService.BonusResult(scansLeft = left, bonusGranted = true)
    }

    override suspend fun getScansLeft(actor: Actor, localDate: LocalDate): Int? {
        if (actor.isPro) return null
        val quota = quotaRepository.getOrCreate(actor.deviceId, localDate)
        return QuotaRules.scansLeft(quota)
    }
}

suspend fun resolveIsPro(deviceId: UUID, userId: UUID?, deviceRepository: DeviceRepository, userRepository: UserRepository): Boolean {
    val now = Instant.now()
    val device = deviceRepository.findById(deviceId)
    val user = userId?.let { userRepository.findById(it) }
    val until = user?.proUntil ?: device?.proUntil
    return until != null && until.isAfter(now)
}
