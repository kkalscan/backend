package ru.kkalscan.data.sqlite

import ru.kkalscan.domain.port.DeviceRecord
import ru.kkalscan.domain.port.DeviceRepository
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.sql.DataSource

class SqliteDeviceRepository(
    private val dataSource: DataSource,
) : DeviceRepository {

    override suspend fun findById(id: UUID): DeviceRecord? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT id, user_id, pro_until
                FROM devices
                WHERE id = ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, id.toString())
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.toDeviceRecord() else null
                }
            }
        }

    override suspend fun getOrCreate(id: UUID): DeviceRecord {
        findById(id)?.let { return it }
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT OR IGNORE INTO devices (id, user_id, pro_until, created_at, last_seen_at)
                VALUES (?, NULL, NULL, ?, ?)
                """.trimIndent(),
            ).use { stmt ->
                val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                stmt.setString(1, id.toString())
                stmt.setString(2, now)
                stmt.setString(3, now)
                stmt.executeUpdate()
            }
        }
        return findById(id) ?: DeviceRecord(id = id, userId = null, proUntil = null)
    }

    override suspend fun updateLastSeen(id: UUID) {
        getOrCreate(id)
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE devices SET last_seen_at = ? WHERE id = ?",
            ).use { stmt ->
                stmt.setString(1, DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                stmt.setString(2, id.toString())
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun linkToUser(deviceId: UUID, userId: UUID) {
        getOrCreate(deviceId)
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE devices SET user_id = ?, pro_until = NULL WHERE id = ?",
            ).use { stmt ->
                stmt.setString(1, userId.toString())
                stmt.setString(2, deviceId.toString())
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun setProUntil(deviceId: UUID, until: Instant?) {
        getOrCreate(deviceId)
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE devices SET pro_until = ? WHERE id = ?",
            ).use { stmt ->
                if (until != null) {
                    stmt.setString(1, DateTimeFormatter.ISO_INSTANT.format(until))
                } else {
                    stmt.setNull(1, Types.VARCHAR)
                }
                stmt.setString(2, deviceId.toString())
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun findByUserId(userId: UUID): List<DeviceRecord> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT id, user_id, pro_until
                FROM devices
                WHERE user_id = ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, userId.toString())
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) add(rs.toDeviceRecord())
                    }
                }
            }
        }

    private fun ResultSet.toDeviceRecord(): DeviceRecord =
        DeviceRecord(
            id = UUID.fromString(getString("id")),
            userId = getString("user_id")?.let { UUID.fromString(it) },
            proUntil = getString("pro_until")?.let { parseInstant(it) },
        )

    private fun parseInstant(raw: String): Instant =
        runCatching { Instant.parse(raw) }.getOrElse {
            LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .toInstant(ZoneOffset.UTC)
        }
}
