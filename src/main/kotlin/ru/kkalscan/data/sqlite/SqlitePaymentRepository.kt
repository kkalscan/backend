package ru.kkalscan.data.sqlite

import ru.kkalscan.domain.port.PaymentRecord
import ru.kkalscan.domain.port.PaymentRepository
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.sql.DataSource

class SqlitePaymentRepository(
    private val dataSource: DataSource,
) : PaymentRepository {

    override suspend fun create(payment: PaymentRecord): UUID {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO payments (
                    id, device_id, user_id, tochka_payment_id,
                    amount_kopecks, tariff, status, created_at, paid_at,
                    promo_code, discount_percent, list_amount_kopecks
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, payment.id.toString())
                stmt.setString(2, payment.deviceId.toString())
                if (payment.userId != null) {
                    stmt.setString(3, payment.userId.toString())
                } else {
                    stmt.setNull(3, Types.VARCHAR)
                }
                if (payment.tochkaPaymentId != null) {
                    stmt.setString(4, payment.tochkaPaymentId)
                } else {
                    stmt.setNull(4, Types.VARCHAR)
                }
                stmt.setInt(5, payment.amountKopecks)
                stmt.setString(6, payment.tariff)
                stmt.setString(7, payment.status)
                stmt.setString(8, DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                if (payment.paidAt != null) {
                    stmt.setString(9, DateTimeFormatter.ISO_INSTANT.format(payment.paidAt))
                } else {
                    stmt.setNull(9, Types.VARCHAR)
                }
                if (payment.promoCode != null) {
                    stmt.setString(10, payment.promoCode)
                } else {
                    stmt.setNull(10, Types.VARCHAR)
                }
                stmt.setInt(11, payment.discountPercent)
                stmt.setInt(12, payment.listAmountKopecks)
                stmt.executeUpdate()
            }
        }
        return payment.id
    }

    override suspend fun markPaid(id: UUID, tochkaPaymentId: String, paidAt: Instant) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                UPDATE payments
                SET status = 'paid', tochka_payment_id = ?, paid_at = ?
                WHERE id = ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, tochkaPaymentId)
                stmt.setString(2, DateTimeFormatter.ISO_INSTANT.format(paidAt))
                stmt.setString(3, id.toString())
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun findById(id: UUID): PaymentRecord? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(SELECT_SQL + " WHERE id = ?").use { stmt ->
                stmt.setString(1, id.toString())
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.toPaymentRecord() else null
                }
            }
        }

    override suspend fun findByTochkaId(tochkaPaymentId: String): PaymentRecord? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(SELECT_SQL + " WHERE tochka_payment_id = ?").use { stmt ->
                stmt.setString(1, tochkaPaymentId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.toPaymentRecord() else null
                }
            }
        }

    override suspend fun findPendingByDevice(deviceId: UUID): List<PaymentRecord> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                SELECT_SQL + " WHERE device_id = ? AND status = 'pending' ORDER BY created_at",
            ).use { stmt ->
                stmt.setString(1, deviceId.toString())
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) add(rs.toPaymentRecord())
                    }
                }
            }
        }

    override suspend fun findAllPending(): List<PaymentRecord> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                SELECT_SQL + " WHERE status = 'pending' ORDER BY created_at",
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) add(rs.toPaymentRecord())
                    }
                }
            }
        }

    private fun ResultSet.toPaymentRecord(): PaymentRecord =
        PaymentRecord(
            id = UUID.fromString(getString("id")),
            deviceId = UUID.fromString(getString("device_id")),
            userId = getString("user_id")?.let { UUID.fromString(it) },
            tochkaPaymentId = getString("tochka_payment_id"),
            amountKopecks = getInt("amount_kopecks"),
            tariff = getString("tariff"),
            status = getString("status"),
            paidAt = getString("paid_at")?.let { parseInstant(it) },
            promoCode = getString("promo_code"),
            discountPercent = getInt("discount_percent"),
            listAmountKopecks = getInt("list_amount_kopecks"),
        )

    private fun parseInstant(raw: String): Instant =
        runCatching { Instant.parse(raw) }.getOrElse {
            LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .toInstant(ZoneOffset.UTC)
        }

    companion object {
        private const val SELECT_SQL = """
            SELECT id, device_id, user_id, tochka_payment_id,
                   amount_kopecks, tariff, status, paid_at,
                   promo_code, discount_percent, list_amount_kopecks
            FROM payments
        """
    }
}
