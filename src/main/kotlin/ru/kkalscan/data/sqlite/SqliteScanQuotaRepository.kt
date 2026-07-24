package ru.kkalscan.data.sqlite

import ru.kkalscan.domain.port.ScanQuotaRecord
import ru.kkalscan.domain.port.ScanQuotaRepository
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

class SqliteScanQuotaRepository(
    private val dataSource: DataSource,
) : ScanQuotaRepository {

    override suspend fun getOrCreate(deviceId: UUID, date: LocalDate): ScanQuotaRecord {
        find(deviceId, date)?.let { return it }
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT OR IGNORE INTO scan_quota (device_id, quota_date, scans_used, bonus_granted, bonus_scans)
                VALUES (?, ?, 0, 0, 0)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, deviceId.toString())
                stmt.setString(2, date.toString())
                stmt.executeUpdate()
            }
        }
        return find(deviceId, date)
            ?: ScanQuotaRecord(deviceId, date, scansUsed = 0, bonusGranted = false, bonusScans = 0)
    }

    override suspend fun incrementUsed(deviceId: UUID, date: LocalDate) {
        getOrCreate(deviceId, date)
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                UPDATE scan_quota
                SET scans_used = scans_used + 1
                WHERE device_id = ? AND quota_date = ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, deviceId.toString())
                stmt.setString(2, date.toString())
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun grantBonus(deviceId: UUID, date: LocalDate) {
        getOrCreate(deviceId, date)
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                UPDATE scan_quota
                SET bonus_granted = 1, bonus_scans = 2
                WHERE device_id = ? AND quota_date = ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, deviceId.toString())
                stmt.setString(2, date.toString())
                stmt.executeUpdate()
            }
        }
    }

    private fun find(deviceId: UUID, date: LocalDate): ScanQuotaRecord? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT device_id, quota_date, scans_used, bonus_granted, bonus_scans
                FROM scan_quota
                WHERE device_id = ? AND quota_date = ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, deviceId.toString())
                stmt.setString(2, date.toString())
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return@use null
                    ScanQuotaRecord(
                        deviceId = UUID.fromString(rs.getString("device_id")),
                        quotaDate = LocalDate.parse(rs.getString("quota_date")),
                        scansUsed = rs.getInt("scans_used"),
                        bonusGranted = rs.getInt("bonus_granted") != 0,
                        bonusScans = rs.getInt("bonus_scans"),
                    )
                }
            }
        }
}
