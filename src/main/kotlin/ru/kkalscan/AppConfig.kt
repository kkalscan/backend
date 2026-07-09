package ru.kkalscan

object AppConfig {
    val port: Int = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val databaseUrl: String = System.getenv("DATABASE_URL") ?: "jdbc:sqlite:./data/kkalscan.db"
    val jwtSecret: String = System.getenv("JWT_SECRET") ?: "dev-secret-change-in-production-min-32-chars"
    val jwtIssuer: String = System.getenv("JWT_ISSUER") ?: "kkalscan"
    val jwtTtlSeconds: Long = System.getenv("JWT_TTL_SECONDS")?.toLongOrNull() ?: 2_592_000L

    /** `stub` — fake dishes; `openrouter` — real vision via OpenRouter */
    val visionProvider: String = System.getenv("VISION_PROVIDER") ?: "stub"
    val visionMonthlyBudgetRub: Int = System.getenv("VISION_MONTHLY_BUDGET_RUB")?.toIntOrNull() ?: 5000
    val visionCostPerRequestRub: Int = System.getenv("VISION_COST_PER_REQUEST_RUB")?.toIntOrNull() ?: 1

    val openRouterApiKey: String = System.getenv("OPENROUTER_API_KEY").orEmpty().trim()
    /** Must exist on https://openrouter.ai/models — `google/gemini-2.0-flash-001` was removed. */
    val openRouterModel: String = normalizeOpenRouterModel(
        System.getenv("OPENROUTER_MODEL")?.trim().takeUnless { it.isNullOrBlank() }
            ?: "google/gemini-2.5-flash",
    )
    val openRouterBaseUrl: String = System.getenv("OPENROUTER_BASE_URL")?.trim().takeUnless { it.isNullOrBlank() }
        ?: "https://openrouter.ai/api/v1"
    val openRouterAppUrl: String = System.getenv("OPENROUTER_APP_URL")?.trim().takeUnless { it.isNullOrBlank() }
        ?: "http://91.207.75.72:8080"
    val openRouterAppName: String = System.getenv("OPENROUTER_APP_NAME")?.trim().takeUnless { it.isNullOrBlank() }
        ?: "KkalScan"

    val smtpHost: String = System.getenv("SMTP_HOST")?.trim().takeUnless { it.isNullOrBlank() }
        ?: "mail.antonbutov.com"
    val smtpPort: Int = System.getenv("SMTP_PORT")?.toIntOrNull() ?: 465
    val smtpUser: String = System.getenv("SMTP_USER").orEmpty().trim()
    val smtpPassword: String = System.getenv("SMTP_PASSWORD").orEmpty()
    val smtpFrom: String = System.getenv("SMTP_FROM")?.trim().takeUnless { it.isNullOrBlank() }
        ?: smtpUser.ifBlank { "noreply@antonbutov.com" }
    val bugReportNotifyTo: String = System.getenv("BUG_REPORT_NOTIFY_TO")?.trim().takeUnless { it.isNullOrBlank() }
        ?: "mail@antonbutov.com"
    val smtpUseTls: Boolean = System.getenv("SMTP_USE_TLS")?.toBooleanStrictOrNull() ?: true

    val smtpConfigured: Boolean get() = smtpUser.isNotBlank() && smtpPassword.isNotBlank()

    /** JWT access token from Tochka OpenAPI onboarding */
    val tochkaAccessToken: String = System.getenv("TOCHKA_ACCESS_TOKEN").orEmpty().trim()
        .ifBlank { System.getenv("TOCHKA_SECRET_KEY").orEmpty().trim() }
    /** Optional override when Tochka returns several customers */
    val tochkaCustomerCodeOverride: String = System.getenv("TOCHKA_CUSTOMER_CODE").orEmpty().trim()
    val tochkaMerchantId: String = System.getenv("TOCHKA_MERCHANT_ID").orEmpty().trim()
    val tochkaWebhookSecret: String = System.getenv("TOCHKA_WEBHOOK_SECRET").orEmpty().trim()
    val tochkaApiBaseUrl: String = System.getenv("TOCHKA_API_BASE_URL")?.trim().takeUnless { it.isNullOrBlank() }
        ?: "https://enter.tochka.com"
    val publicBaseUrl: String = System.getenv("PUBLIC_BASE_URL")?.trim().takeUnless { it.isNullOrBlank() }
        ?: openRouterAppUrl.trimEnd('/')

    val tochkaConfigured: Boolean get() = tochkaAccessToken.isNotBlank()

    /** Shared secret for POST /api/v1/payments/test/activate (falls back to JWT_SECRET). */
    val testPaymentSecret: String = System.getenv("TEST_PAYMENT_SECRET").orEmpty().trim()
        .ifBlank { jwtSecret }

    val testPaymentNotifyTo: String = System.getenv("TEST_PAYMENT_NOTIFY_TO")?.trim().takeUnless { it.isNullOrBlank() }
        ?: bugReportNotifyTo

    val testPaymentEnabled: Boolean get() = testPaymentSecret.isNotBlank()

    /**
     * When true, POST /api/v1/payments/pro/start activates Pro without Tochka payment.
     * Set FREE_PRO_ACTIVATION=false on prod when real billing is ready.
     */
    val freeProActivationEnabled: Boolean =
        System.getenv("FREE_PRO_ACTIVATION")?.toBooleanStrictOrNull() ?: true

    const val ACTIVITY_EMULATOR_FULL_DAYLIGHT_ACTIVE_KCAL = 1500
    const val ACTIVITY_EMULATOR_DAYLIGHT_START_HOUR = 7
    const val ACTIVITY_EMULATOR_DAYLIGHT_END_HOUR = 23
    const val ACTIVITY_EMULATOR_BMR_DEFAULT = 1500
    const val ACTIVITY_EMULATOR_KCAL_PER_STEP = 0.04
    const val ACTIVITY_EMULATOR_LOOKBACK_DAYS = 30
}

internal fun normalizeOpenRouterModel(model: String): String =
    when (model) {
        "google/gemini-2.0-flash-001",
        "google/gemini-2.0-flash-exp:free",
        -> "google/gemini-2.5-flash"
        else -> model
    }
