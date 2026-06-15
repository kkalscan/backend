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
}

internal fun normalizeOpenRouterModel(model: String): String =
    when (model) {
        "google/gemini-2.0-flash-001",
        "google/gemini-2.0-flash-exp:free",
        -> "google/gemini-2.5-flash"
        else -> model
    }
