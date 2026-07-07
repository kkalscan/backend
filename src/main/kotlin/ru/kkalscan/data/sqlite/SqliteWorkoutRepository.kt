package ru.kkalscan.data.sqlite

import ru.kkalscan.domain.port.WorkoutRecord
import ru.kkalscan.domain.port.WorkoutRepository
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.sql.DataSource

class SqliteWorkoutRepository(
    private val dataSource: DataSource,
) : WorkoutRepository {

    override suspend fun findByDevice(deviceId: UUID, date: LocalDate, tzOffsetMin: Int): List<WorkoutRecord> =
        filterByDate(loadByDevice(deviceId), date, tzOffsetMin)

    override suspend fun findByUser(userId: UUID, date: LocalDate, tzOffsetMin: Int): List<WorkoutRecord> =
        filterByDate(loadByUser(userId), date, tzOffsetMin)

    override suspend fun insert(workout: WorkoutRecord): WorkoutRecord {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO workout_entries (id, device_id, user_id, name, kcal, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, workout.id.toString())
                stmt.setString(2, workout.deviceId.toString())
                if (workout.userId != null) {
                    stmt.setString(3, workout.userId.toString())
                } else {
                    stmt.setNull(3, Types.VARCHAR)
                }
                stmt.setString(4, workout.name)
                stmt.setInt(5, workout.kcal)
                stmt.setString(6, DateTimeFormatter.ISO_INSTANT.format(workout.createdAt))
                stmt.executeUpdate()
            }
        }
        return workout
    }

    override suspend fun delete(id: UUID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM workout_entries WHERE id = ?").use { stmt ->
                stmt.setString(1, id.toString())
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun findById(id: UUID): WorkoutRecord? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT id, device_id, user_id, name, kcal, created_at
                FROM workout_entries
                WHERE id = ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, id.toString())
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.toWorkoutRecord() else null
                }
            }
        }

    override suspend fun updateUserIdForDevice(deviceId: UUID, userId: UUID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE workout_entries SET user_id = ? WHERE device_id = ?",
            ).use { stmt ->
                stmt.setString(1, userId.toString())
                stmt.setString(2, deviceId.toString())
                stmt.executeUpdate()
            }
        }
    }

    private fun loadByDevice(deviceId: UUID): List<WorkoutRecord> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT id, device_id, user_id, name, kcal, created_at
                FROM workout_entries
                WHERE device_id = ?
                ORDER BY created_at
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, deviceId.toString())
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.toWorkoutRecord())
                        }
                    }
                }
            }
        }

    private fun loadByUser(userId: UUID): List<WorkoutRecord> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT id, device_id, user_id, name, kcal, created_at
                FROM workout_entries
                WHERE user_id = ?
                ORDER BY created_at
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, userId.toString())
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.toWorkoutRecord())
                        }
                    }
                }
            }
        }

    private fun filterByDate(rows: List<WorkoutRecord>, date: LocalDate, tzOffsetMin: Int): List<WorkoutRecord> {
        val offset = ZoneOffset.ofTotalSeconds(tzOffsetMin * 60)
        return rows.filter { it.createdAt.atOffset(offset).toLocalDate() == date }
            .sortedBy { it.createdAt }
    }

    private fun ResultSet.toWorkoutRecord(): WorkoutRecord =
        WorkoutRecord(
            id = UUID.fromString(getString("id")),
            deviceId = UUID.fromString(getString("device_id")),
            userId = getString("user_id")?.let(UUID::fromString),
            name = getString("name"),
            kcal = getInt("kcal"),
            createdAt = parseInstant(getString("created_at")),
        )

    private fun parseInstant(raw: String): Instant =
        runCatching { Instant.parse(raw) }.getOrElse {
            LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .toInstant(ZoneOffset.UTC)
        }
}
