package ru.kkalscan.domain.port

import java.util.UUID

data class BugReportMailPayload(
    val reportId: UUID,
    val deviceId: UUID,
    val email: String,
    val description: String,
    val screenshots: List<BugReportScreenshotAttachment>,
)

data class BugReportScreenshotAttachment(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
)

interface BugReportMailer {
    suspend fun sendBugReportNotification(payload: BugReportMailPayload)
}
