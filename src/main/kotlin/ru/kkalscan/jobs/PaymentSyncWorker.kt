package ru.kkalscan.jobs

import io.ktor.server.application.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.kkalscan.AppConfig
import ru.kkalscan.domain.port.PaymentService

/**
 * Backend-driven Tochka payment sync while webhooks require HTTPS:443 (no domain yet).
 * Polls all pending payments and activates Pro when Tochka reports PAID/SUCCESS/APPROVED.
 */
fun Application.startPaymentSyncWorker(paymentService: PaymentService) {
    val intervalSeconds = AppConfig.paymentSyncIntervalSeconds
    if (intervalSeconds <= 0L) {
        environment.log.info("Payment sync worker disabled (PAYMENT_SYNC_INTERVAL_SECONDS=0)")
        return
    }
    if (!AppConfig.tochkaConfigured) {
        environment.log.info("Payment sync worker skipped (Tochka not configured)")
        return
    }

    environment.log.info("Payment sync worker started interval={}s", intervalSeconds)
    launch {
        while (isActive) {
            delay(intervalSeconds * 1_000)
            runCatching { paymentService.syncAllPendingPayments() }
                .onFailure { error ->
                    environment.log.warn("Payment sync failed: {}", error.message)
                }
        }
    }
}
