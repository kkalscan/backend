package ru.kkalscan.integrations

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.util.ByteArrayDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import ru.kkalscan.domain.port.BugReportMailPayload
import ru.kkalscan.domain.port.BugReportMailer
import java.util.Properties

class SmtpBugReportMailer(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    private val fromAddress: String,
    private val notifyTo: String,
    private val useTls: Boolean = true,
) : BugReportMailer {

    private val log = LoggerFactory.getLogger(SmtpBugReportMailer::class.java)

    override suspend fun sendBugReportNotification(payload: BugReportMailPayload) = withContext(Dispatchers.IO) {
        val props = Properties().apply {
            put("mail.smtp.host", host)
            put("mail.smtp.port", port.toString())
            put("mail.smtp.auth", "true")
            if (useTls) {
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
            }
        }
        val session = Session.getInstance(
            props,
            object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication =
                    PasswordAuthentication(username, password)
            },
        )

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(fromAddress, "KkalScan"))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(notifyTo))
            replyTo = arrayOf(InternetAddress(payload.email))
            subject = "KkalScan bug report ${payload.reportId}"
        }

        val multipart = MimeMultipart()
        val textPart = MimeBodyPart().apply {
            setText(
                buildString {
                    appendLine("Новый баг-репорт KkalScan")
                    appendLine()
                    appendLine("Report ID: ${payload.reportId}")
                    appendLine("Device ID: ${payload.deviceId}")
                    appendLine("Email автора: ${payload.email}")
                    appendLine()
                    appendLine("Описание:")
                    appendLine(payload.description)
                    appendLine()
                    appendLine("Скриншотов: ${payload.screenshots.size}")
                },
                "UTF-8",
            )
        }
        multipart.addBodyPart(textPart)

        payload.screenshots.forEach { screenshot ->
            val attachment = MimeBodyPart().apply {
                dataHandler = jakarta.activation.DataHandler(
                    ByteArrayDataSource(screenshot.bytes, screenshot.contentType),
                )
                fileName = screenshot.fileName
            }
            multipart.addBodyPart(attachment)
        }

        message.setContent(multipart)
        Transport.send(message)
        log.info("bug report email sent to {} for report {}", notifyTo, payload.reportId)
    }
}

class LoggingBugReportMailer : BugReportMailer {
    val sent = mutableListOf<BugReportMailPayload>()

    override suspend fun sendBugReportNotification(payload: BugReportMailPayload) {
        sent.add(payload)
    }
}
