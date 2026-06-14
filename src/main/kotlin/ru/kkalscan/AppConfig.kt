package ru.kkalscan

object AppConfig {
    val port: Int = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val databaseUrl: String = System.getenv("DATABASE_URL") ?: "jdbc:sqlite:./data/kkalscan.db"
    val jwtSecret: String = System.getenv("JWT_SECRET") ?: "dev-secret-change-in-production-min-32"
    val jwtIssuer: String = System.getenv("JWT_ISSUER") ?: "kkalscan"
    val visionProvider: String = System.getenv("VISION_PROVIDER") ?: "gemini"
    val visionMonthlyBudgetRub: Int = System.getenv("VISION_MONTHLY_BUDGET_RUB")?.toIntOrNull() ?: 5000
}
