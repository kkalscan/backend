package ru.kkalscan.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.nio.file.Files
import java.nio.file.Paths
import javax.sql.DataSource

object DatabaseFactory {
    @Volatile
    private var dataSource: HikariDataSource? = null

    fun init(jdbcUrl: String): DataSource {
        dataSource?.let { return it }
        ensureSqliteParentDir(jdbcUrl)
        val ds = HikariDataSource(
            HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                maximumPoolSize = 5
                isAutoCommit = true
            },
        )
        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        dataSource = ds
        return ds
    }

    private fun ensureSqliteParentDir(jdbcUrl: String) {
        if (!jdbcUrl.startsWith("jdbc:sqlite:")) return
        val path = jdbcUrl.removePrefix("jdbc:sqlite:")
        if (path == ":memory:" || path.startsWith("file:memdb")) return
        val file = Paths.get(path)
        file.parent?.let { Files.createDirectories(it) }
    }
}
