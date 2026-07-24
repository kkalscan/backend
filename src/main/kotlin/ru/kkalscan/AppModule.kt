package ru.kkalscan

import ru.kkalscan.data.memory.InMemoryRepositories
import ru.kkalscan.data.sqlite.SqliteBugReportRepository
import ru.kkalscan.data.sqlite.SqliteFeatureSearchRepository
import ru.kkalscan.data.sqlite.SqliteSearchLogRepository
import ru.kkalscan.data.sqlite.SqliteDailyActivityRepository
import ru.kkalscan.data.sqlite.SqliteDeviceRepository
import ru.kkalscan.data.sqlite.SqliteDiaryRepository
import ru.kkalscan.data.sqlite.SqlitePaymentRepository
import ru.kkalscan.data.sqlite.SqlitePromoPurchaseRepository
import ru.kkalscan.data.sqlite.SqliteWorkoutRepository
import ru.kkalscan.domain.port.PromoPurchaseRepository
import ru.kkalscan.domain.port.ActivityEmulatorService
import ru.kkalscan.domain.port.AuthService
import ru.kkalscan.domain.port.BugReportMailer
import ru.kkalscan.domain.port.BugReportRepository
import ru.kkalscan.domain.port.BugReportService
import ru.kkalscan.domain.port.DailyActivityRepository
import ru.kkalscan.domain.port.DeviceRepository
import ru.kkalscan.domain.port.DiaryRepository
import ru.kkalscan.domain.port.DiaryService
import ru.kkalscan.domain.port.FeatureSearchRepository
import ru.kkalscan.domain.port.FeatureSearchService
import ru.kkalscan.domain.port.FoodSearchService
import ru.kkalscan.domain.port.SearchLogRepository
import ru.kkalscan.domain.port.IdentityResolver
import ru.kkalscan.domain.port.InsightService
import ru.kkalscan.domain.port.PaymentRepository
import ru.kkalscan.domain.port.PaymentService
import ru.kkalscan.domain.port.PlainTextMailer
import ru.kkalscan.domain.port.QuotaService
import ru.kkalscan.domain.port.ScanService
import ru.kkalscan.domain.port.SubscriptionService
import ru.kkalscan.domain.port.WorkoutRepository
import ru.kkalscan.domain.service.ActivityEmulatorServiceImpl
import ru.kkalscan.domain.service.AccountMergeServiceImpl
import ru.kkalscan.domain.service.AuthServiceImpl
import ru.kkalscan.domain.service.BugReportServiceImpl
import ru.kkalscan.domain.service.DiaryServiceImpl
import ru.kkalscan.domain.service.FeatureSearchServiceImpl
import ru.kkalscan.domain.service.FoodSearchServiceImpl
import ru.kkalscan.domain.service.IdentityResolverImpl
import ru.kkalscan.domain.service.InsightServiceImpl
import ru.kkalscan.domain.service.JwtIssuer
import ru.kkalscan.domain.service.PaymentServiceImpl
import ru.kkalscan.domain.service.PromoService
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

    val workoutRepository: WorkoutRepository =
        dataSource?.let { SqliteWorkoutRepository(it) } ?: repos.workouts

    val diaryRepository: DiaryRepository =
        dataSource?.let { SqliteDiaryRepository(it) } ?: repos.diary

    val dailyActivityRepository: DailyActivityRepository =
        dataSource?.let { SqliteDailyActivityRepository(it) } ?: repos.dailyActivity

    val promoPurchaseRepository: PromoPurchaseRepository =
        dataSource?.let { SqlitePromoPurchaseRepository(it) } ?: repos.promoPurchases

    val deviceRepository: DeviceRepository =
        dataSource?.let { SqliteDeviceRepository(it) } ?: repos.devices

    val paymentRepository: PaymentRepository =
        dataSource?.let { SqlitePaymentRepository(it) } ?: repos.payments

    val foodSearchService: FoodSearchService = FoodSearchServiceImpl(searchLogRepository)

    val featureSearchService: FeatureSearchService =
        FeatureSearchServiceImpl(
            featureSearchRepository,
            searchLogRepository,
            visionClient,
            repos.visionBudget,
        )

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

    val quotaService: QuotaService = QuotaServiceImpl(repos.quotas, deviceRepository, repos.users)

    val identityResolver: IdentityResolver = IdentityResolverImpl(
        deviceRepository,
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
        diaryRepository,
        workoutRepository,
        dailyActivityRepository,
        quotaService,
        repos.scanSessions,
        visionClient,
        repos.visionBudget,
    )

    val insightService: InsightService = InsightServiceImpl(
        diaryRepository,
        workoutRepository,
        visionClient,
        repos.visionBudget,
    )

    val activityEmulatorService: ActivityEmulatorService =
        ActivityEmulatorServiceImpl(workoutRepository, dailyActivityRepository)

    val subscriptionService: SubscriptionService = SubscriptionServiceImpl(
        deviceRepository,
        repos.users,
    )

    val accountMergeService = AccountMergeServiceImpl(
        deviceRepository,
        repos.users,
        diaryRepository,
        workoutRepository,
        dailyActivityRepository,
    )

    val authService: AuthService = AuthServiceImpl(
        vkAuthClient,
        repos.oauth,
        repos.users,
        deviceRepository,
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

    val promoService: PromoService = PromoService(
        repos.promoCodes,
        repos.devicePromoBindings,
    )

    val paymentService: PaymentService = PaymentServiceImpl(
        paymentRepository,
        deviceRepository,
        subscriptionService,
        tochkaClient,
        plainTextMailer,
        promoService,
        promoPurchaseRepository,
    )

    val bugReportService: BugReportService = BugReportServiceImpl(
        bugReportRepository,
        subscriptionService,
        bugReportMailer,
    )
}
