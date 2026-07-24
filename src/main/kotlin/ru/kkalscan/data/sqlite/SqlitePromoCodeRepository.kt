package ru.kkalscan.data.sqlite

import ru.kkalscan.domain.port.PromoCode
import ru.kkalscan.domain.port.PromoCodeRepository
import javax.sql.DataSource

class SqlitePromoCodeRepository(
    private val dataSource: DataSource,
) : PromoCodeRepository {

    override fun findActive(code: String): PromoCode? {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return null
        return dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT code, discount_percent, active
                FROM promo_codes
                WHERE lower(code) = lower(?) AND active = 1
                LIMIT 1
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, trimmed)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return@use null
                    PromoCode(
                        code = rs.getString("code"),
                        discountPercent = rs.getInt("discount_percent"),
                        active = rs.getInt("active") != 0,
                    )
                }
            }
        }
    }

    override fun upsert(promo: PromoCode) {
        val code = promo.code.trim()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO promo_codes (code, discount_percent, active)
                VALUES (?, ?, ?)
                ON CONFLICT(code) DO UPDATE SET
                    discount_percent = excluded.discount_percent,
                    active = excluded.active
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, code)
                stmt.setInt(2, promo.discountPercent)
                stmt.setInt(3, if (promo.active) 1 else 0)
                stmt.executeUpdate()
            }
        }
    }
}
