package ru.kkalscan.domain.service

import kotlinx.coroutines.test.runTest
import ru.kkalscan.TestFixtures
import ru.kkalscan.data.memory.InMemoryRepositories
import ru.kkalscan.domain.BonusAlreadyUsedException
import ru.kkalscan.domain.LimitHitException
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QuotaServiceTest {
    private val repos = InMemoryRepositories()
    private val service = QuotaServiceImpl(repos.quotas, repos.devices, repos.users)
    private val date = LocalDate.of(2026, 6, 14)
    private val actor = TestFixtures.guestActor()

    @Test
    fun `free user starts with 3 scans left`() = runTest {
        assertEquals(3, service.getScansLeft(actor, date))
        assertTrue(service.canStartScan(actor, date))
    }

    @Test
    fun `consume scan decrements quota`() = runTest {
        service.consumeScan(actor, date, null)
        assertEquals(2, service.getScansLeft(actor, date))
    }

    @Test
    fun `limit hit after 3 consumes`() = runTest {
        repeat(3) { service.consumeScan(actor, date, null) }
        assertEquals(0, service.getScansLeft(actor, date))
        assertFailsWith<LimitHitException> { service.consumeScan(actor, date, null) }
    }

    @Test
    fun `ad bonus grants 2 extra scans once per day`() = runTest {
        repeat(3) { service.consumeScan(actor, date, null) }
        val bonus = service.grantAdBonus(actor, date)
        assertEquals(2, bonus.scansLeft)
        assertFailsWith<BonusAlreadyUsedException> { service.grantAdBonus(actor, date) }
    }

    @Test
    fun `pro user has unlimited scans`() = runTest {
        val pro = TestFixtures.proActor()
        assertEquals(null, service.getScansLeft(pro, date))
        repeat(10) { service.consumeScan(pro, date, null) }
        assertEquals(null, service.getScansLeft(pro, date))
    }
}
