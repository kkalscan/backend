package ru.kkalscan.data.sqlite

import ru.kkalscan.domain.port.BugReportRepository
import ru.kkalscan.domain.port.BugReportScreenshotRecord
import java.sql.Types
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class SqliteBugReportRepository(
    private val dataSource: DataSource,
) : BugReportRepository {

    override suspend fun hasReportForDevice(deviceId: UUID): Boolean =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT 1 FROM bug_reports WHERE device_id = ? LIMIT 1",
            ).use { stmt ->
                stmt.setString(1, deviceId.toString())
                stmt.executeQuery().next()
            }
        }

    override suspend fun create(
        deviceId: UUID,
        userId: UUID?,
        email: String,
        description: String,
        screenshots: List<ByteArray>,
    ): UUID {
        val reportId = UUID.randomUUID()
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement(
                    """
                    INSERT INTO bug_reports (id, device_id, user_id, email, description, created_at)
                    VALUES (?, ?, ?, ?, ?, datetime('now'))
                    """.trimIndent(),
                ).use { stmt ->
                    stmt.setString(1, reportId.toString())
                    stmt.setString(2, deviceId.toString())
                    if (userId != null) {
                        stmt.setString(3, userId.toString())
                    } else {
                        stmt.setNull(3, Types.VARCHAR)
                    }
                    stmt.setString(4, email)
                    stmt.setString(5, description)
                    stmt.executeUpdate()
                }

                conn.prepareStatement(
                    """
                    INSERT INTO bug_report_screenshots (id, report_id, position, content_type, data, created_at)
                    VALUES (?, ?, ?, ?, ?, datetime('now'))
                    """.trimIndent(),
                ).use { stmt ->
                    screenshots.forEachIndexed { index, bytes ->
                        stmt.setString(1, UUID.randomUUID().toString())
                        stmt.setString(2, reportId.toString())
                        stmt.setInt(3, index)
                        stmt.setString(4, "image/jpeg")
                        stmt.setBytes(5, bytes)
                        stmt.addBatch()
                    }
                    if (screenshots.isNotEmpty()) {
                        stmt.executeBatch()
                    }
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
        return reportId
    }

    override suspend fun deleteReport(reportId: UUID) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("DELETE FROM bug_reports WHERE id = ?").use { stmt ->
                stmt.setString(1, reportId.toString())
                stmt.executeUpdate()
            }
        }
    }

    fun countScreenshots(reportId: UUID): Int =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT COUNT(*) FROM bug_report_screenshots WHERE report_id = ?",
            ).use { stmt ->
                stmt.setString(1, reportId.toString())
                stmt.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }

    fun findScreenshots(reportId: UUID): List<BugReportScreenshotRecord> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT id, position, content_type, length(data) AS size_bytes
                FROM bug_report_screenshots
                WHERE report_id = ?
                ORDER BY position
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, reportId.toString())
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                BugReportScreenshotRecord(
                                    id = UUID.fromString(rs.getString("id")),
                                    reportId = reportId,
                                    position = rs.getInt("position"),
                                    contentType = rs.getString("content_type"),
                                    sizeBytes = rs.getLong("size_bytes"),
                                    createdAt = java.time.Instant.EPOCH,
                                ),
                            )
                        }
                    }
                }
            }
        }
}
