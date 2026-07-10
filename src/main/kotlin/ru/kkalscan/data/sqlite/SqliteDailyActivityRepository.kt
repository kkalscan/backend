package ru.kkalscan.data.sqlite

import ru.kkalscan.domain.port.ActivitySourceKind
import ru.kkalscan.domain.port.DailyActivityRecord
import ru.kkalscan.domain.port.DailyActivityRepository
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.sql.DataSource

class SqliteDailyActivityRepository(
    private val dataSource: DataSource,
) : DailyActivityRepository {

    override suspend fun findByDevice(deviceId: UUID, date: LocalDate): DailyActivityRecord? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT device_id, user_id, local_date, steps, kcal, source, updated_at
                FROM daily_activity
                WHERE device_id = ? AND local_date = ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, deviceId.toString())
                stmt.setString(2, date.toString())
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.toRecord() else null
                }
            }
        }

    override suspend fun findByUser(userId: UUID, date: LocalDate): DailyActivityRecord? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT device_id, user_id, local_date, steps, kcal, source, updated_at
                FROM daily_activity
                WHERE user_id = ? AND local_date = ?
                ORDER BY updated_at DESC
                LIMIT 1
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, userId.toString())
                stmt.setString(2, date.toString())
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.toRecord() else null
                }
            }
        }

    override suspend fun upsert(record: DailyActivityRecord): DailyActivityRecord {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO daily_activity (device_id, user_id, local_date, steps, kcal, source, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(device_id, local_date) DO UPDATE SET
                    user_id = excluded.user_id,
                    steps = excluded.steps,
                    kcal = excluded.kcal,
                    source = excluded.source,
                    updated_at = excluded.updated_at
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, record.deviceId.toString())
                if (record.userId != null) {
                    stmt.setString(2, record.userId.toString())
                } else {
                    stmt.setNull(2, Types.VARCHAR)
                }
                stmt.setString(3, record.localDate.toString())
                stmt.setInt(4, record.steps)
                stmt.setInt(5, record.kcal)
                stmt.setString(6, record.source.wireName())
                stmt.setString(7, DateTimeFormatter.ISO_INSTANT.format(record.updatedAt))
                stmt.executeUpdate()
            }
        }
        return record
    }

    override suspend fun updateUserIdForDevice(deviceId: UUID, userId: UUID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE daily_activity SET user_id = ? WHERE device_id = ?",
            ).use { stmt ->
                stmt.setString(1, userId.toString())
                stmt.setString(2, deviceId.toString())
                stmt.executeUpdate()
            }
        }
    }

    private fun ResultSet.toRecord(): DailyActivityRecord =
        DailyActivityRecord(
            deviceId = UUID.fromString(getString("device_id")),
            userId = getString("user_id")?.let(UUID::fromString),
            localDate = LocalDate.parse(getString("local_date")),
            steps = getInt("steps"),
            kcal = getInt("kcal"),
            source = parseSource(getString("source")),
            updatedAt = parseInstant(getString("updated_at")),
        )

    private fun parseInstant(raw: String): Instant =
        runCatching { Instant.parse(raw) }.getOrElse {
            LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .toInstant(java.time.ZoneOffset.UTC)
        }

    private fun parseSource(raw: String): ActivitySourceKind =
        when (raw) {
            "device_sensor" -> ActivitySourceKind.DeviceSensor
            "emulator" -> ActivitySourceKind.Emulator
            else -> ActivitySourceKind.None
        }

    private fun ActivitySourceKind.wireName(): String =
        when (this) {
            ActivitySourceKind.DeviceSensor -> "device_sensor"
            ActivitySourceKind.Emulator -> "emulator"
            ActivitySourceKind.None -> "none"
        }
}
