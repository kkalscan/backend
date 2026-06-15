package ru.kkalscan.domain.service

import org.slf4j.LoggerFactory
import ru.kkalscan.domain.BadRequestException
import ru.kkalscan.domain.BugReportAlreadyUsedException
import ru.kkalscan.domain.model.Actor
import ru.kkalscan.domain.port.BugReportMailPayload
import ru.kkalscan.domain.port.BugReportMailer
import ru.kkalscan.domain.port.BugReportRepository
import ru.kkalscan.domain.port.BugReportScreenshotAttachment
import ru.kkalscan.domain.port.BugReportService
import java.time.Instant
import java.util.UUID

class BugReportServiceImpl(
    private val bugReportRepository: BugReportRepository,
    private val subscriptionService: ru.kkalscan.domain.port.SubscriptionService,
    private val bugReportMailer: BugReportMailer,
) : BugReportService {

    private val log = LoggerFactory.getLogger(BugReportServiceImpl::class.java)

    override suspend fun submitBugReport(
        actor: Actor,
        email: String,
        description: String,
        screenshots: List<ByteArray>,
    ): BugReportService.BugReportResult {
        val normalizedEmail = email.trim().lowercase()
        validateEmail(normalizedEmail)
        validateDescription(description)
        validateScreenshots(screenshots)

        if (bugReportRepository.hasReportForDevice(actor.deviceId)) {
            throw BugReportAlreadyUsedException()
        }

        val reportId = bugReportRepository.create(
            deviceId = actor.deviceId,
            userId = actor.userId,
            email = normalizedEmail,
            description = description.trim(),
            screenshots = screenshots,
        )

        try {
            bugReportMailer.sendBugReportNotification(
                BugReportMailPayload(
                    reportId = reportId,
                    deviceId = actor.deviceId,
                    email = normalizedEmail,
                    description = description.trim(),
                    screenshots = screenshots.mapIndexed { index, bytes ->
                        BugReportScreenshotAttachment(
                            fileName = "screenshot-$index.jpg",
                            contentType = "image/jpeg",
                            bytes = bytes,
                        )
                    },
                ),
            )
        } catch (e: Exception) {
            bugReportRepository.deleteReport(reportId)
            log.error("failed to send bug report email for {}", reportId, e)
            throw BadRequestException("Не удалось отправить уведомление о баге: ${e.message ?: "SMTP error"}")
        }

        val paidAt = Instant.now()
        subscriptionService.activatePro(actor.deviceId, TARIFF, paidAt)
        val status = subscriptionService.getStatus(actor)

        log.info(
            "bug report {} from device {} email {} ({} screenshots), pro until {}",
            reportId,
            actor.deviceId,
            normalizedEmail,
            screenshots.size,
            status.proUntil,
        )

        return BugReportService.BugReportResult(
            reportId = reportId,
            isPro = status.isPro,
            proUntil = status.proUntil,
            message = "Спасибо! Pro на месяц активирован.",
        )
    }

    private fun validateEmail(email: String) {
        if (email.isBlank() || !EMAIL_REGEX.matches(email)) {
            throw BadRequestException("Укажите корректный email")
        }
    }

    private fun validateDescription(description: String) {
        val trimmed = description.trim()
        when {
            trimmed.length < MIN_DESCRIPTION_LENGTH ->
                throw BadRequestException("Опишите баг подробнее (минимум $MIN_DESCRIPTION_LENGTH символов)")
            trimmed.length > MAX_DESCRIPTION_LENGTH ->
                throw BadRequestException("Описание слишком длинное (максимум $MAX_DESCRIPTION_LENGTH символов)")
        }
    }

    private fun validateScreenshots(screenshots: List<ByteArray>) {
        if (screenshots.size > MAX_SCREENSHOTS) {
            throw BadRequestException("Не больше $MAX_SCREENSHOTS скриншотов")
        }
        screenshots.forEachIndexed { index, bytes ->
            if (bytes.isEmpty()) {
                throw BadRequestException("Скриншот ${index + 1} пустой")
            }
            if (bytes.size > MAX_SCREENSHOT_BYTES) {
                throw BadRequestException("Скриншот ${index + 1} больше ${MAX_SCREENSHOT_BYTES / 1024} КБ")
            }
        }
    }

    companion object {
        const val TARIFF = "pro_bug_report_30d"
        private const val MIN_DESCRIPTION_LENGTH = 10
        private const val MAX_DESCRIPTION_LENGTH = 2000
        private const val MAX_SCREENSHOTS = 3
        private const val MAX_SCREENSHOT_BYTES = 600 * 1024
        private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
