package ru.kkalscan.integrations.tochka

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import ru.kkalscan.AppConfig
import ru.kkalscan.domain.port.TochkaClient
import ru.kkalscan.integrations.StubTochkaClient

object TochkaClientFactory {
    fun create(httpClient: HttpClient = HttpClient(CIO)): TochkaClient =
        if (AppConfig.tochkaConfigured) {
            HttpTochkaClient(httpClient)
        } else {
            StubTochkaClient()
        }
}
