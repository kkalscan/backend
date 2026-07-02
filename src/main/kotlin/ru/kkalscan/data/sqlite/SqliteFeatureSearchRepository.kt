package ru.kkalscan.data.sqlite

import ru.kkalscan.domain.port.FeatureSearchItemRecord
import ru.kkalscan.domain.port.FeatureSearchRepository
import javax.sql.DataSource

class SqliteFeatureSearchRepository(
    private val dataSource: DataSource,
) : FeatureSearchRepository {

    override suspend fun listEnabled(locale: String): List<FeatureSearchItemRecord> =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                SELECT id, title, subtitle, keywords, deeplink, icon, sort_order
                FROM feature_search_items
                WHERE enabled = 1 AND locale = ?
                ORDER BY sort_order ASC
                """.trimIndent(),
            ).use { stmt ->
                stmt.setString(1, locale)
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                FeatureSearchItemRecord(
                                    id = rs.getString("id"),
                                    title = rs.getString("title"),
                                    subtitle = rs.getString("subtitle"),
                                    keywords = rs.getString("keywords"),
                                    deeplink = rs.getString("deeplink"),
                                    icon = rs.getString("icon"),
                                    sortOrder = rs.getInt("sort_order"),
                                ),
                            )
                        }
                    }
                }
            }
        }
}
