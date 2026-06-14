package ru.kkalscan

object AppConfig {
    val port: Int = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val databaseUrl: String = System.getenv("DATABASE_URL") ?: "jdbc:sqlite:./data/kkalscan.db"
    val jwtSecret: String = System.getenv("JWT_SECRET") ?: "dev-secret-change-in-production-min-32-chars"
    val jwtIssuer: String = System.getenv("JWT_ISSUER") ?: "kkalscan"
    val jwtTtlSeconds: Long = System.getenv("JWT_TTL_SECONDS")?.toLongOrNull() ?: 2_592_000L
    val visionProvider: String = System.getenv("VISION_PROVIDER") ?: "stub"
    val visionMonthlyBudgetRub: Int = System.getenv("VISION_MONTHLY_BUDGET_RUB")?.toIntOrNull() ?: 5000
    val visionCostPerRequestRub: Int = System.getenv("VISION_COST_PER_REQUEST_RUB")?.toIntOrNull() ?: 1
}
