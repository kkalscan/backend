package ru.kkalscan.domain.port

interface PlainTextMailer {
    suspend fun send(to: String, subject: String, body: String)
}
