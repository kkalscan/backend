package ru.kkalscan.integrations

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import ru.kkalscan.domain.port.PlainTextMailer
import java.util.Properties

class SmtpPlainTextMailer(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    private val fromAddress: String,
    private val useTls: Boolean = true,
) : PlainTextMailer {

    private val log = LoggerFactory.getLogger(SmtpPlainTextMailer::class.java)

    override suspend fun send(to: String, subject: String, body: String) = withContext(Dispatchers.IO) {
        val session = createSession()
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(fromAddress, "KkalScan"))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            setSubject(subject, "UTF-8")
            setText(body, "UTF-8")
        }
        Transport.send(message)
        log.info("email sent to {} subject={}", to, subject)
    }

    private fun createSession(): Session {
        val props = Properties().apply {
            put("mail.smtp.host", host)
            put("mail.smtp.port", port.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.ssl.trust", host)
            put("mail.smtp.ssl.checkserveridentity", "false")
            when {
                useTls && port == 465 -> {
                    put("mail.smtp.ssl.enable", "true")
                    put("mail.smtp.socketFactory.port", port.toString())
                    put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                    put("mail.smtp.socketFactory.fallback", "false")
                }
                useTls -> {
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.starttls.required", "true")
                }
            }
        }
        return Session.getInstance(
            props,
            object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication =
                    PasswordAuthentication(username, password)
            },
        )
    }
}

class LoggingPlainTextMailer : PlainTextMailer {
    val sent = mutableListOf<Triple<String, String, String>>()

    override suspend fun send(to: String, subject: String, body: String) {
        sent.add(Triple(to, subject, body))
    }
}
