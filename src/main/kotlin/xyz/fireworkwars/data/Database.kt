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

                // social: a friendship is stored once per side, so "friends of X" is one indexed lookup.
                // unannounced: friend accepted uuid's request while uuid was offline, and uuid hasn't been told yet
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS friends (
                        uuid TEXT NOT NULL REFERENCES players(uuid),
                        friend TEXT NOT NULL REFERENCES players(uuid),
                        since BIGINT NOT NULL,
                        unannounced BOOLEAN NOT NULL DEFAULT FALSE,
                        PRIMARY KEY (uuid, friend)
                    )
                """.trimIndent())

                statement.execute("ALTER TABLE friends ADD COLUMN IF NOT EXISTS unannounced BOOLEAN NOT NULL DEFAULT FALSE")

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS friend_requests (
                        sender TEXT NOT NULL REFERENCES players(uuid),
                        target TEXT NOT NULL REFERENCES players(uuid),
                        sent_at BIGINT NOT NULL,
                        PRIMARY KEY (sender, target)
                    )
                """.trimIndent())

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS blocks (
                        uuid TEXT NOT NULL REFERENCES players(uuid),
                        blocked TEXT NOT NULL REFERENCES players(uuid),
                        since BIGINT NOT NULL,
                        PRIMARY KEY (uuid, blocked)
                    )
                """.trimIndent())

                statement.execute("""
                    CREATE TABLE IF NOT EXISTS social_settings (
                        uuid TEXT PRIMARY KEY REFERENCES players(uuid),
                        friend_requests TEXT NOT NULL DEFAULT 'EVERYONE',
                        messages TEXT NOT NULL DEFAULT 'EVERYONE',
                        party_invites TEXT NOT NULL DEFAULT 'FRIENDS'
                    )
                """.trimIndent())

                statement.execute("CREATE INDEX IF NOT EXISTS idx_friend_requests_target ON friend_requests(target)")
            }
        }
    }

    fun close() {
        if (::dataSource.isInitialized) dataSource.close()
    }
}
