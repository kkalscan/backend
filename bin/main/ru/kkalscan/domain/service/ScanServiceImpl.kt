package ru.kkalscan.domain.service

import org.slf4j.LoggerFactory
import ru.kkalscan.AppConfig
import ru.kkalscan.domain.BadRequestException
import ru.kkalscan.domain.ForbiddenException
import ru.kkalscan.domain.LimitHitException
import ru.kkalscan.domain.NotFoundException
import ru.kkalscan.domain.VisionBudgetExceededException
import ru.kkalscan.domain.VisionUnavailableException
import ru.kkalscan.domain.model.Actor
import ru.kkalscan.domain.model.DishDto
import ru.kkalscan.domain.model.MacroTotals
import ru.kkalscan.domain.port.ScanService
import ru.kkalscan.domain.port.ScanSessionRepository
import ru.kkalscan.domain.port.VisionBudgetRepository
import ru.kkalscan.domain.port.VisionClient
import ru.kkalscan.domain.port.QuotaService
import java.time.LocalDate
import java.time.YearMonth

class ScanServiceImpl(
    private val quotaService: QuotaService,
    private val visionClient: VisionClient,
    private val scanSessionRepository: ScanSessionRepository,
    private val visionBudgetRepository: VisionBudgetRepository,
) : ScanService {

    private val log = LoggerFactory.getLogger(ScanServiceImpl::class.java)

    override suspend fun analyzePhoto(
        actor: Actor,
        photoBytes: ByteArray,
        localDate: LocalDate,
        timezoneOffsetMinutes: Int,
    ): ScanService.ScanResult {
        if (photoBytes.isEmpty() || photoBytes.size > MAX_PHOTO_BYTES) {
            throw BadRequestException("Фото должно быть JPEG до 600 KB")
        }
        if (!quotaService.canStartScan(actor, localDate)) {
            val left = quotaService.getScansLeft(actor, localDate) ?: 0
            log.info("scan limit_hit device={} left={}", mask(actor.deviceId), left)
            throw LimitHitException(left)
        }

        log.info(
            "scan start device={} photoBytes={} provider={}",
            mask(actor.deviceId),
            photoBytes.size,
            AppConfig.visionProvider,
        )

        val month = YearMonth.from(localDate)
        if (visionBudgetRepository.getMonthCost(month) >= AppConfig.visionMonthlyBudgetRub) {
            throw VisionBudgetExceededException()
        }

        val dishes = try {
            visionClient.analyzeFood(photoBytes)
        } catch (e: Exception) {
            log.warn("scan vision_failed device={}: {}", mask(actor.deviceId), e.message)
            throw VisionUnavailableException(cause = e)
        }

        if (dishes.isEmpty()) {
            log.warn("scan no_food device={}", mask(actor.deviceId))
            throw VisionUnavailableException("На фото не видно еду. Сфотографируйте тарелку крупнее.")
        }

        visionBudgetRepository.addCost(month, AppConfig.visionCostPerRequestRub)

        val scanId = scanSessionRepository.create(actor.deviceId, dishes)
        val totals = MacroTotals.from(dishes)

        log.info(
            "scan ok device={} scanId={} dishes={} kcal={} left={}",
            mask(actor.deviceId),
            scanId.toString().take(8),
            dishes.size,
            totals.kcal,
            quotaService.getScansLeft(actor, localDate),
        )

        return ScanService.ScanResult(
            scanId = scanId,
            dishes = dishes,
            totals = totals,
            scansLeft = quotaService.getScansLeft(actor, localDate),
            isPro = actor.isPro,
        )
    }

    companion object {
        const val MAX_PHOTO_BYTES = 600 * 1024

        private fun mask(deviceId: java.util.UUID): String = deviceId.toString().take(8) + "…"
    }
}
