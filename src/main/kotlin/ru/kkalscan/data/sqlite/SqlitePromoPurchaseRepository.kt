package ru.kkalscan.data.sqlite

import ru.kkalscan.domain.port.PromoPurchaseRecord
import ru.kkalscan.domain.port.PromoPurchaseRepository
import java.sql.Types
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.sql.DataSource

class SqlitePromoPurchaseRepository(
    private val dataSource: DataSource,
) : PromoPurchaseRepository {

    override suspend fun record(record: PromoPurchaseRecord) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO promo_purchases (
                    id, payment_id, device_id, tariff,
                    amount_kopecks, list_amount_kopecks,
                    promo_code, discount_percent, status, paid_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, record.id.toString())
                stmt.setString(2, record.paymentId.toString())
                stmt.setString(3, record.deviceId.toString())
                stmt.setString(4, record.tariff)
                stmt.setInt(5, record.amountKopecks)
                stmt.setInt(6, record.listAmountKopecks)
                if (record.promoCode != null) {
                    stmt.setString(7, record.promoCode)
                } else {
                    stmt.setNull(7, Types.VARCHAR)
                }
                stmt.setInt(8, record.discountPercent)
                stmt.setString(9, record.status)
                stmt.setString(10, record.paidAt.toString())
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun findById(id: UUID): PromoPurchaseRecord? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT id, payment_id, device_id, tariff,
                       amount_kopecks, list_amount_kopecks,
                       promo_code, discount_percent, status, paid_at
                FROM promo_purchases
                WHERE id = ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, id.toString())
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return null
                    PromoPurchaseRecord(
                        id = UUID.fromString(rs.getString("id")),
                        paymentId = UUID.fromString(rs.getString("payment_id")),
                        deviceId = UUID.fromString(rs.getString("device_id")),
                        tariff = rs.getString("tariff"),
                        amountKopecks = rs.getInt("amount_kopecks"),
                        listAmountKopecks = rs.getInt("list_amount_kopecks"),
                        promoCode = rs.getString("promo_code"),
                        discountPercent = rs.getInt("discount_percent"),
                        status = rs.getString("status"),
                        paidAt = parseInstant(rs.getString("paid_at")),
                    )
                }
            }
        }

    private fun parseInstant(raw: String): Instant =
        runCatching { Instant.parse(raw) }.getOrElse {
            LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .toInstant(ZoneOffset.UTC)
        }
}
