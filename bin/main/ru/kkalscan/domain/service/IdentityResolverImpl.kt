package ru.kkalscan.domain.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import ru.kkalscan.AppConfig
import ru.kkalscan.domain.UnauthorizedException
import ru.kkalscan.domain.model.Actor
import ru.kkalscan.domain.model.OAuthProvider
import ru.kkalscan.domain.port.DeviceRepository
import ru.kkalscan.domain.port.IdentityResolver
import ru.kkalscan.domain.port.OAuthRepository
import ru.kkalscan.domain.port.UserRepository
import java.time.Instant
import java.util.Date
import java.util.UUID

class IdentityResolverImpl(
    private val deviceRepository: DeviceRepository,
    private val userRepository: UserRepository,
    private val oauthRepository: OAuthRepository,
) : IdentityResolver {

    override suspend fun resolve(deviceId: UUID, bearerToken: String?): Actor {
        deviceRepository.updateLastSeen(deviceId)
        val device = deviceRepository.getOrCreate(deviceId)

        val userIdFromJwt = bearerToken?.takeIf { it.isNotBlank() }?.let { parseUserId(it) }
        val userId = userIdFromJwt ?: device.userId

        val linkedProviders = userId?.let { oauthRepository.listProviders(it) } ?: emptyList()
        val isPro = resolveIsPro(deviceId, userId, deviceRepository, userRepository)

        return Actor(
            deviceId = deviceId,
            userId = userId,
            isPro = isPro,
            accountLinked = userId != null && linkedProviders.isNotEmpty(),
            linkedProviders = linkedProviders,
        )
    }

    private fun parseUserId(token: String): UUID {
        return try {
            val jwt = token.removePrefix("Bearer ").trim()
            val verifier = JWT.require(Algorithm.HMAC256(AppConfig.jwtSecret))
                .withIssuer(AppConfig.jwtIssuer)
                .build()
            val decoded = verifier.verify(jwt)
            UUID.fromString(decoded.subject)
        } catch (_: Exception) {
            throw UnauthorizedException("Невалидный токен")
        }
    }
}

class JwtIssuer {
    fun issue(userId: UUID, deviceId: UUID?): String {
        val builder = JWT.create()
            .withIssuer(AppConfig.jwtIssuer)
            .withSubject(userId.toString())
            .withExpiresAt(Date.from(Instant.now().plusSeconds(AppConfig.jwtTtlSeconds)))
        deviceId?.let { builder.withClaim("device_id", it.toString()) }
        return builder.sign(Algorithm.HMAC256(AppConfig.jwtSecret))
    }
}
