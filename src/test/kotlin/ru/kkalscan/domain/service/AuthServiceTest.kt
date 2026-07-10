package ru.kkalscan.domain.service

import kotlinx.coroutines.test.runTest
import ru.kkalscan.TestFixtures
import ru.kkalscan.data.memory.InMemoryRepositories
import ru.kkalscan.domain.UnauthorizedException
import ru.kkalscan.domain.model.OAuthProvider
import ru.kkalscan.integrations.StubVkAuthClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AuthServiceTest {
    private val repos = InMemoryRepositories()
    private val subscriptionService = SubscriptionServiceImpl(repos.devices, repos.users)
    private val mergeService = AccountMergeServiceImpl(repos.devices, repos.users, repos.diary, repos.workouts, repos.dailyActivity)
    private val service = AuthServiceImpl(
        StubVkAuthClient(),
        repos.oauth,
        repos.users,
        repos.devices,
        mergeService,
        subscriptionService,
        JwtIssuer(),
    )
    private val deviceId = TestFixtures.deviceId

    @Test
    fun `link vk returns jwt and links account`() = runTest {
        val response = service.linkVk(deviceId, "vk_test_424242")

        assertTrue(response.accessToken.isNotBlank())
        assertEquals("Bearer", response.tokenType)
        assertTrue(response.accountLinked)
        assertEquals(listOf(OAuthProvider.vk), response.linkedProviders)
    }

    @Test
    fun `invalid vk token rejected`() = runTest {
        assertFailsWith<UnauthorizedException> {
            service.linkVk(deviceId, "not-a-vk-token")
        }
    }

    @Test
    fun `getMe returns linked user`() = runTest {
        val linked = service.linkVk(deviceId, "vk_test_777")

        val me = service.getMe(linked.userId)

        assertEquals(linked.userId, me.userId)
        assertEquals(listOf(OAuthProvider.vk), me.linkedProviders)
        assertTrue(me.devices.contains(deviceId))
    }
}
