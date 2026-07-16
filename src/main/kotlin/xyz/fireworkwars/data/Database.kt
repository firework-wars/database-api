package xyz.fireworkwars.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection

class Database(private val settings: ConnectionSettings) {

    private lateinit var dataSource: HikariDataSource

    fun start() {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://${settings.databaseHost}:${settings.databasePort}/${settings.databaseName}"
            username = settings.databaseUser
            password = settings.databasePassword
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            minimumIdle = 2
            connectionTestQuery = "SELECT 1"
        }

        dataSource = HikariDataSource(config)
    }

    fun getConnection(): Connection = dataSource.connection

    fun migrate() {
        getConnection().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS players (
                        uuid TEXT PRIMARY KEY,
                        rank TEXT NOT NULL DEFAULT 'NONE',
                        first_join BIGINT NOT NULL,
                        last_seen BIGINT NOT NULL,
                        play_time BIGINT NOT NULL DEFAULT 0,
                        names TEXT NOT NULL DEFAULT '[]',
                        client_brands TEXT NOT NULL DEFAULT '[]',
                        ips TEXT NOT NULL DEFAULT '[]'
                    )
                """.trimIndent())

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_stats (
                        uuid TEXT PRIMARY KEY REFERENCES players(uuid),
                        wins INTEGER NOT NULL DEFAULT 0,
                        losses INTEGER NOT NULL DEFAULT 0,
                        draws INTEGER NOT NULL DEFAULT 0,
                        kills INTEGER NOT NULL DEFAULT 0,
                        deaths INTEGER NOT NULL DEFAULT 0,
                        damage DOUBLE PRECISION NOT NULL DEFAULT 0.0,
                        winstreak INTEGER NOT NULL DEFAULT 0,
                        highest_winstreak INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS stat_events (
                        id BIGSERIAL PRIMARY KEY,
                        uuid TEXT NOT NULL REFERENCES players(uuid),
                        type TEXT NOT NULL,
                        game_type TEXT,
                        timestamp BIGINT NOT NULL
                    )
                """.trimIndent())

                statement.execute("ALTER TABLE stat_events ADD COLUMN IF NOT EXISTS game_type TEXT")

                statement.execute("CREATE INDEX IF NOT EXISTS idx_stat_events_type_ts ON stat_events(type, timestamp)")
                statement.execute("CREATE INDEX IF NOT EXISTS idx_stat_events_gametype_type_ts ON stat_events(game_type, type, timestamp)")
                statement.execute("CREATE INDEX IF NOT EXISTS idx_stat_events_uuid ON stat_events(uuid)")
            }
        }
    }

    fun close() {
        if (::dataSource.isInitialized) dataSource.close()
    }
}
