package ru.kkalscan.data.sqlite

import ru.kkalscan.domain.port.SearchLogRecord
import ru.kkalscan.domain.port.SearchLogRepository
import ru.kkalscan.domain.port.SearchQueryStat
import java.util.UUID
import javax.sql.DataSource

class SqliteSearchLogRepository(
    private val dataSource: DataSource,
) : SearchLogRepository {

    override suspend fun log(record: SearchLogRecord) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO search_logs (id, device_id, query, query_normalized, source, results_count, created_at)
                VALUES (?, ?, ?, ?, ?, ?, datetime('now'))
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, record.id.toString())
                stmt.setString(2, record.deviceId.toString())
                stmt.setString(3, record.query)
                stmt.setString(4, record.queryNormalized)
                stmt.setString(5, record.source)
                stmt.setInt(6, record.resultsCount)
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun topQueries(days: Int, limit: Int): List<SearchQueryStat> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT query_normalized, COUNT(*) AS cnt
                FROM search_logs
                WHERE created_at >= datetime('now', '-' || ? || ' days')
                GROUP BY query_normalized
                ORDER BY cnt DESC, query_normalized ASC
                LIMIT ?
                """.trimIndent(),
            ).use { stmt ->
                stmt.setInt(1, days)
                stmt.setInt(2, limit)
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                SearchQueryStat(
                                    query = rs.getString("query_normalized"),
                                    count = rs.getInt("cnt"),
                                ),
                            )
                        }
                    }
                }
            }
        }

    fun countAll(): Int =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM search_logs").use { stmt ->
                stmt.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }
}
