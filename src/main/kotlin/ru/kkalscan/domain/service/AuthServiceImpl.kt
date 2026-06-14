package ru.kkalscan.domain.service

import ru.kkalscan.domain.UnauthorizedException
import ru.kkalscan.domain.model.OAuthProvider
import ru.kkalscan.domain.port.AccountMergeService
import ru.kkalscan.domain.port.AuthService
import ru.kkalscan.domain.port.AuthTokenResponse
import ru.kkalscan.domain.port.DeviceRepository
import ru.kkalscan.domain.port.MeResponse
import ru.kkalscan.domain.port.OAuthRepository
import ru.kkalscan.domain.port.SubscriptionService
import ru.kkalscan.domain.port.UserRepository
import ru.kkalscan.domain.port.VkAuthClient
import ru.kkalscan.domain.service.SubscriptionServiceImpl.Companion.TARIFF
import java.util.UUID

class AuthServiceImpl(
    private val vkAuthClient: VkAuthClient,
    private val oauthRepository: OAuthRepository,
    private val userRepository: UserRepository,
    private val deviceRepository: DeviceRepository,
    private val accountMergeService: AccountMergeService,
    private val subscriptionService: SubscriptionService,
    private val jwtIssuer: JwtIssuer,
) : AuthService {

    override suspend fun linkVk(deviceId: UUID, vkAccessToken: String): AuthTokenResponse =
        linkOAuth(deviceId, vkAccessToken, OAuthProvider.vk) {
            vkAuthClient.verifyToken(it).id.toString()
        }

    override suspend fun linkYandex(deviceId: UUID, yandexToken: String): AuthTokenResponse {
        throw UnsupportedOperationException("Yandex auth in v0.1.1")
    }

    private suspend fun linkOAuth(
        deviceId: UUID,
        token: String,
        provider: OAuthProvider,
        resolveProviderUserId: suspend (String) -> String,
    ): AuthTokenResponse {
        val providerUserId = try {
            resolveProviderUserId(token)
        } catch (_: Exception) {
            throw UnauthorizedException("Невалидный токен провайдера")
        }

        val userId = oauthRepository.findByProvider(provider, providerUserId)
            ?: userRepository.create().also { oauthRepository.link(it, provider, providerUserId) }

        accountMergeService.mergeDeviceToUser(deviceId, userId)

        deviceRepository.getOrCreate(deviceId)
        val actor = IdentityResolverImpl(deviceRepository, userRepository, oauthRepository)
            .resolve(deviceId, null)

        val status = subscriptionService.getStatus(actor.copy(userId = userId, accountLinked = true, linkedProviders = listOf(provider)))

        return AuthTokenResponse(
            accessToken = jwtIssuer.issue(userId, deviceId),
            expiresIn = ru.kkalscan.AppConfig.jwtTtlSeconds,
            userId = userId,
            isPro = status.isPro,
            accountLinked = true,
            linkedProviders = listOf(provider),
        )
    }

    override suspend fun getMe(userId: UUID): MeResponse {
        val user = userRepository.findById(userId) ?: throw UnauthorizedException()
        val devices = deviceRepository.findByUserId(userId)
        val providers = oauthRepository.listProviders(userId)
        val isPro = user.proUntil?.isAfter(java.time.Instant.now()) == true

        return MeResponse(
            userId = userId,
            isPro = isPro,
            proUntil = user.proUntil,
            linkedProviders = providers,
            devices = devices.map { it.id },
        )
    }

    override fun issueJwt(userId: UUID, deviceId: UUID?): String = jwtIssuer.issue(userId, deviceId)
}
