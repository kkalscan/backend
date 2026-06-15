package ru.kkalscan.domain.service

import kotlinx.coroutines.test.runTest
import ru.kkalscan.TestFixtures
import ru.kkalscan.data.memory.InMemoryRepositories
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubscriptionServiceTest {
    private val repos = InMemoryRepositories()
    private val service = SubscriptionServiceImpl(repos.devices, repos.users)
    private val deviceId = TestFixtures.deviceId

    @Test
    fun `activate pro sets status`() = runTest {
        service.activatePro(deviceId, SubscriptionServiceImpl.TARIFF, Instant.now())
        val proUntil = Instant.now().plus(30, ChronoUnit.DAYS)
        repos.devices.setProUntil(deviceId, proUntil)

        val actor = TestFixtures.guestActor().copy(isPro = true)
        val status = service.getStatus(actor)

        assertTrue(status.isPro)
        assertEquals(SubscriptionServiceImpl.TARIFF, status.tariff)
    }
}
