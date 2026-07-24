package ru.kkalscan.data.sqlite

import ru.kkalscan.domain.model.DishDto
import ru.kkalscan.domain.model.MealType
import ru.kkalscan.domain.port.DiaryEntryRecord
import ru.kkalscan.domain.port.DiaryRepository
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.sql.DataSource

class SqliteDiaryRepository(
    private val dataSource: DataSource,
) : DiaryRepository {

    override suspend fun findEntriesByDevice(deviceId: UUID, date: LocalDate, tzOffsetMin: Int): List<DiaryEntryRecord> =
        filterByDate(loadByDevice(deviceId), date, tzOffsetMin)

    override suspend fun findEntriesByUser(userId: UUID, date: LocalDate, tzOffsetMin: Int): List<DiaryEntryRecord> =
        filterByDate(loadByUser(userId), date, tzOffsetMin)

    override suspend fun consumedKcalByDay(
        deviceId: UUID,
        from: LocalDate,
        to: LocalDate,
        tzOffsetMin: Int,
    ): Map<LocalDate, Int> {
        val offset = ZoneOffset.ofTotalSeconds(tzOffsetMin * 60)
        return loadByDevice(deviceId)
            .asSequence()
            .map { entry ->
                entry.createdAt.atOffset(offset).toLocalDate() to entry.totalKcal
            }
            .filter { (date, _) -> !date.isBefore(from) && !date.isAfter(to) }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, kcals) -> kcals.sum() }
    }

    override suspend fun insertEntry(entry: DiaryEntryRecord, dishes: List<DishDto>): DiaryEntryRecord {
        val stored = entry.copy(dishes = dishes)
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement(
                    """
                    INSERT INTO diary_entries
                        (id, device_id, user_id, meal_type, scan_session_id, total_kcal, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { stmt ->
                    stmt.setString(1, stored.id.toString())
                    stmt.setString(2, stored.deviceId.toString())
                    if (stored.userId != null) {
                        stmt.setString(3, stored.userId.toString())
                    } else {
                        stmt.setNull(3, Types.VARCHAR)
                    }
                    stmt.setString(4, stored.mealType.name)
                    if (stored.scanSessionId != null) {
                        stmt.setString(5, stored.scanSessionId.toString())
                    } else {
                        stmt.setNull(5, Types.VARCHAR)
                    }
                    stmt.setInt(6, stored.totalKcal)
                    stmt.setString(7, DateTimeFormatter.ISO_INSTANT.format(stored.createdAt))
                    stmt.executeUpdate()
                }
                insertDishes(conn, stored.id, dishes)
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
        return stored
    }

    override suspend fun deleteEntry(id: UUID) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement("DELETE FROM diary_dishes WHERE entry_id = ?").use { stmt ->
                    stmt.setString(1, id.toString())
                    stmt.executeUpdate()
                }
                conn.prepareStatement("DELETE FROM diary_entries WHERE id = ?").use { stmt ->
                    stmt.setString(1, id.toString())
                    stmt.executeUpdate()
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }

    override suspend fun findEntry(id: UUID): DiaryEntryRecord? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT id, device_id, user_id, meal_type, scan_session_id, total_kcal, created_at
                FROM diary_entries
                WHERE id = ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, id.toString())
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return@use null
                    val entry = rs.toEntryWithoutDishes()
                    entry.copy(dishes = loadDishes(conn, entry.id))
                }
            }
        }

    override suspend fun updateUserIdForDevice(deviceId: UUID, userId: UUID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE diary_entries SET user_id = ? WHERE device_id = ?",
            ).use { stmt ->
                stmt.setString(1, userId.toString())
                stmt.setString(2, deviceId.toString())
                stmt.executeUpdate()
            }
        }
    }

    private fun loadByDevice(deviceId: UUID): List<DiaryEntryRecord> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT id, device_id, user_id, meal_type, scan_session_id, total_kcal, created_at
                FROM diary_entries
                WHERE device_id = ?
                ORDER BY created_at
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, deviceId.toString())
                loadEntriesWithDishes(conn, stmt.executeQuery())
            }
        }

    private fun loadByUser(userId: UUID): List<DiaryEntryRecord> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT id, device_id, user_id, meal_type, scan_session_id, total_kcal, created_at
                FROM diary_entries
                WHERE user_id = ?
                ORDER BY created_at
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, userId.toString())
                loadEntriesWithDishes(conn, stmt.executeQuery())
            }
        }

    private fun loadEntriesWithDishes(conn: Connection, rs: ResultSet): List<DiaryEntryRecord> {
        rs.use {
            val entries = buildList {
                while (rs.next()) {
                    add(rs.toEntryWithoutDishes())
                }
            }
            return entries.map { entry ->
                entry.copy(dishes = loadDishes(conn, entry.id))
            }
        }
    }

    private fun loadDishes(conn: Connection, entryId: UUID): List<DishDto> =
        conn.prepareStatement(
            """
            SELECT name, grams, kcal, protein, fat, carbs, fiber
            FROM diary_dishes
            WHERE entry_id = ?
            ORDER BY rowid
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, entryId.toString())
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            DishDto(
                                name = rs.getString("name"),
                                grams = rs.getInt("grams"),
                                kcal = rs.getInt("kcal"),
                                protein = rs.getDouble("protein"),
                                fat = rs.getDouble("fat"),
                                carbs = rs.getDouble("carbs"),
                                fiber = rs.getDouble("fiber"),
                            ),
                        )
                    }
                }
            }
        }

    private fun insertDishes(conn: Connection, entryId: UUID, dishes: List<DishDto>) {
        conn.prepareStatement(
            """
            INSERT INTO diary_dishes (id, entry_id, name, grams, kcal, protein, fat, carbs, fiber)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { stmt ->
            for (dish in dishes) {
                stmt.setString(1, UUID.randomUUID().toString())
                stmt.setString(2, entryId.toString())
                stmt.setString(3, dish.name)
                stmt.setInt(4, dish.grams)
                stmt.setInt(5, dish.kcal)
                stmt.setDouble(6, dish.protein)
                stmt.setDouble(7, dish.fat)
                stmt.setDouble(8, dish.carbs)
                stmt.setDouble(9, dish.fiber)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    private fun filterByDate(list: List<DiaryEntryRecord>, date: LocalDate, tzOffsetMin: Int): List<DiaryEntryRecord> {
        val offset = ZoneOffset.ofTotalSeconds(tzOffsetMin * 60)
        return list.filter { it.createdAt.atOffset(offset).toLocalDate() == date }
            .sortedBy { it.createdAt }
    }

    private fun ResultSet.toEntryWithoutDishes(): DiaryEntryRecord =
        DiaryEntryRecord(
            id = UUID.fromString(getString("id")),
            deviceId = UUID.fromString(getString("device_id")),
            userId = getString("user_id")?.let(UUID::fromString),
            mealType = MealType.valueOf(getString("meal_type")),
            scanSessionId = getString("scan_session_id")?.let(UUID::fromString),
            totalKcal = getInt("total_kcal"),
            createdAt = parseInstant(getString("created_at")),
            dishes = emptyList(),
        )

    private fun parseInstant(raw: String): Instant =
        runCatching { Instant.parse(raw) }.getOrElse {
            LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .toInstant(ZoneOffset.UTC)
        }
}
