package ru.kkalscan

import ru.kkalscan.data.memory.InMemoryRepositories
import ru.kkalscan.domain.port.AuthService
import ru.kkalscan.domain.port.DiaryService
import ru.kkalscan.domain.port.IdentityResolver
import ru.kkalscan.domain.port.PaymentService
import ru.kkalscan.domain.port.QuotaService
import ru.kkalscan.domain.port.ScanService
import ru.kkalscan.domain.port.SubscriptionService
import ru.kkalscan.domain.service.AccountMergeServiceImpl
import ru.kkalscan.domain.service.AuthServiceImpl
import ru.kkalscan.domain.service.DiaryServiceImpl
import ru.kkalscan.domain.service.IdentityResolverImpl
import ru.kkalscan.domain.service.JwtIssuer
import ru.kkalscan.domain.service.PaymentServiceImpl
import ru.kkalscan.domain.service.QuotaServiceImpl
import ru.kkalscan.domain.service.ScanServiceImpl
import ru.kkalscan.domain.service.SubscriptionServiceImpl
import ru.kkalscan.integrations.StubTochkaClient
import ru.kkalscan.integrations.StubVkAuthClient
import ru.kkalscan.integrations.VisionClientFactory
import ru.kkalscan.domain.port.VisionClient

data class AppModule(
    val repos: InMemoryRepositories = InMemoryRepositories(),
    val visionClient: VisionClient = VisionClientFactory.create(),
    val vkAuthClient: StubVkAuthClient = StubVkAuthClient(),
    val tochkaClient: StubTochkaClient = StubTochkaClient(),
) {
    val jwtIssuer = JwtIssuer()

    val quotaService: QuotaService = QuotaServiceImpl(repos.quotas, repos.devices, repos.users)

    val identityResolver: IdentityResolver = IdentityResolverImpl(
        repos.devices,
        repos.users,
        repos.oauth,
    )

    val scanService: ScanService = ScanServiceImpl(
        quotaService,
        visionClient,
        repos.scanSessions,
        repos.visionBudget,
    )

    val diaryService: DiaryService = DiaryServiceImpl(
        repos.diary,
        quotaService,
        repos.scanSessions,
    )

    val subscriptionService: SubscriptionService = SubscriptionServiceImpl(
        repos.devices,
        repos.users,
    )

    val accountMergeService = AccountMergeServiceImpl(
        repos.devices,
        repos.users,
        repos.diary,
    )

    val authService: AuthService = AuthServiceImpl(
        vkAuthClient,
        repos.oauth,
        repos.users,
        repos.devices,
        accountMergeService,
        subscriptionService,
        jwtIssuer,
    )

    val paymentService: PaymentService = PaymentServiceImpl(
        repos.payments,
        repos.devices,
        subscriptionService,
        tochkaClient,
    )
}
