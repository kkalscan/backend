package ru.kkalscan

import ru.kkalscan.data.memory.InMemoryRepositories
import ru.kkalscan.data.sqlite.SqliteBugReportRepository
import ru.kkalscan.data.sqlite.SqliteFeatureSearchRepository
import ru.kkalscan.data.sqlite.SqliteSearchLogRepository
import ru.kkalscan.domain.port.AuthService
import ru.kkalscan.domain.port.BugReportMailer
import ru.kkalscan.domain.port.BugReportRepository
import ru.kkalscan.domain.port.BugReportService
import ru.kkalscan.domain.port.DiaryService
import ru.kkalscan.domain.port.FeatureSearchRepository
import ru.kkalscan.domain.port.FeatureSearchService
import ru.kkalscan.domain.port.FoodSearchService
import ru.kkalscan.domain.port.SearchLogRepository
import ru.kkalscan.domain.port.IdentityResolver
import ru.kkalscan.domain.port.PaymentService
import ru.kkalscan.domain.port.PlainTextMailer
import ru.kkalscan.domain.port.QuotaService
import ru.kkalscan.domain.port.ScanService
import ru.kkalscan.domain.port.SubscriptionService
import ru.kkalscan.domain.service.AccountMergeServiceImpl
import ru.kkalscan.domain.service.AuthServiceImpl
import ru.kkalscan.domain.service.BugReportServiceImpl
import ru.kkalscan.domain.service.DiaryServiceImpl
import ru.kkalscan.domain.service.FeatureSearchServiceImpl
import ru.kkalscan.domain.service.FoodSearchServiceImpl
import ru.kkalscan.domain.service.IdentityResolverImpl
import ru.kkalscan.domain.service.JwtIssuer
import ru.kkalscan.domain.service.PaymentServiceImpl
import ru.kkalscan.domain.service.QuotaServiceImpl
import ru.kkalscan.domain.service.ScanServiceImpl
import ru.kkalscan.domain.service.SubscriptionServiceImpl
import ru.kkalscan.integrations.LoggingBugReportMailer
import ru.kkalscan.integrations.LoggingPlainTextMailer
import ru.kkalscan.integrations.SmtpBugReportMailer
import ru.kkalscan.integrations.SmtpPlainTextMailer
import ru.kkalscan.integrations.StubVkAuthClient
import ru.kkalscan.integrations.tochka.TochkaClientFactory
import ru.kkalscan.integrations.VisionClientFactory
import ru.kkalscan.domain.port.TochkaClient
import ru.kkalscan.domain.port.VisionClient
import javax.sql.DataSource

data class AppModule(
    val repos: InMemoryRepositories = InMemoryRepositories(),
    val visionClient: VisionClient = VisionClientFactory.create(),
    val vkAuthClient: StubVkAuthClient = StubVkAuthClient(),
    val tochkaClient: TochkaClient = TochkaClientFactory.create(),
    val dataSource: DataSource? = null,
    val bugReportMailerOverride: BugReportMailer? = null,
) {
    val bugReportRepository: BugReportRepository =
        dataSource?.let { SqliteBugReportRepository(it) } ?: repos.bugReports

    val searchLogRepository: SearchLogRepository =
        dataSource?.let { SqliteSearchLogRepository(it) } ?: repos.searchLogs

    val featureSearchRepository: FeatureSearchRepository =
        dataSource?.let { SqliteFeatureSearchRepository(it) } ?: repos.featureSearch

    val foodSearchService: FoodSearchService = FoodSearchServiceImpl(searchLogRepository)

    val featureSearchService: FeatureSearchService =
        FeatureSearchServiceImpl(featureSearchRepository, searchLogRepository)

    val bugReportMailer: BugReportMailer = bugReportMailerOverride ?: if (AppConfig.smtpConfigured) {
        SmtpBugReportMailer(
            host = AppConfig.smtpHost,
            port = AppConfig.smtpPort,
            username = AppConfig.smtpUser,
            password = AppConfig.smtpPassword,
            fromAddress = AppConfig.smtpFrom,
            notifyTo = AppConfig.bugReportNotifyTo,
            useTls = AppConfig.smtpUseTls,
        )
    } else {
        LoggingBugReportMailer()
    }
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

    val plainTextMailer: PlainTextMailer = if (AppConfig.smtpConfigured) {
        SmtpPlainTextMailer(
            host = AppConfig.smtpHost,
            port = AppConfig.smtpPort,
            username = AppConfig.smtpUser,
            password = AppConfig.smtpPassword,
            fromAddress = AppConfig.smtpFrom,
            useTls = AppConfig.smtpUseTls,
        )
    } else {
        LoggingPlainTextMailer()
    }

    val paymentService: PaymentService = PaymentServiceImpl(
        repos.payments,
        repos.devices,
        subscriptionService,
        tochkaClient,
        plainTextMailer,
    )

    val bugReportService: BugReportService = BugReportServiceImpl(
        bugReportRepository,
        subscriptionService,
        bugReportMailer,
    )
}
