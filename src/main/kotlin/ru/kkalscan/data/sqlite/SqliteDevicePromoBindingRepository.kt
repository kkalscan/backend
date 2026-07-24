package ru.kkalscan.data.sqlite

import ru.kkalscan.domain.port.DevicePromoBindingRepository
import java.util.UUID
import javax.sql.DataSource

class SqliteDevicePromoBindingRepository(
    private val dataSource: DataSource,
) : DevicePromoBindingRepository {

    override fun getBoundCode(deviceId: UUID): String? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT promo_code FROM device_promo_bindings WHERE device_id = ?",
            ).use { stmt ->
                stmt.setString(1, deviceId.toString())
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getString("promo_code") else null
                }
            }
        }

    override fun bind(deviceId: UUID, promoCode: String) {
        val code = promoCode.trim()
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO device_promo_bindings (device_id, promo_code, bound_at)
                VALUES (?, ?, datetime('now'))
                ON CONFLICT(device_id) DO UPDATE SET
                    promo_code = excluded.promo_code,
                    bound_at = excluded.bound_at
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, deviceId.toString())
                stmt.setString(2, code)
                stmt.executeUpdate()
            }
        }
    }
}
