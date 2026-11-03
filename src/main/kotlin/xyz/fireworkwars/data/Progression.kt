package xyz.fireworkwars.data

import java.util.UUID

data class PlayerStats(
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val kills: Int,
    val deaths: Int,
    val winstreak: Int,
    val highestWinstreak: Int,
    val xp: Long
) {
    val games: Int get() = wins + losses + draws
    val level: Int get() = Levels.levelOf(xp)
}

@Suppress("unused")
class Progression(private val database: Database) {

    fun getXp(uuid: UUID): Long = getStats(uuid)?.xp ?: 0L

    fun addXp(uuid: UUID, amount: Int): Long {
        database.getConnection().use { connection ->
            connection.prepareStatement("UPDATE player_stats SET xp = xp + ? WHERE uuid = ? RETURNING xp").use { statement ->
                statement.setInt(1, amount)
                statement.setString(2, uuid.toString())

                val resultSet = statement.executeQuery()
                return if (resultSet.next()) resultSet.getLong(1) else 0L
            }
        }
    }

    fun getStats(uuid: UUID): PlayerStats? {
        database.getConnection().use { connection ->
            connection.prepareStatement("SELECT * FROM player_stats WHERE uuid = ?").use { statement ->
                statement.setString(1, uuid.toString())

                val resultSet = statement.executeQuery()
                if (!resultSet.next()) return null

                return PlayerStats(
                    wins = resultSet.getInt("wins"),
                    losses = resultSet.getInt("losses"),
                    draws = resultSet.getInt("draws"),
                    kills = resultSet.getInt("kills"),
                    deaths = resultSet.getInt("deaths"),
                    winstreak = resultSet.getInt("winstreak"),
                    highestWinstreak = resultSet.getInt("highest_winstreak"),
                    xp = resultSet.getLong("xp")
                )
            }
        }
    }

    fun getStatsByGame(uuid: UUID): Map<String, Map<StatType, Int>> {
        database.getConnection().use { connection ->
            connection.prepareStatement("SELECT game_type, type, COUNT(*) FROM stat_events WHERE uuid = ? AND game_type IS NOT NULL GROUP BY game_type, type").use { statement ->
                statement.setString(1, uuid.toString())

                val resultSet = statement.executeQuery()
                val stats = mutableMapOf<String, MutableMap<StatType, Int>>()

                while (resultSet.next()) {
                    val type = runCatching { StatType.valueOf(resultSet.getString(2)) }.getOrNull() ?: continue
                    stats.getOrPut(resultSet.getString(1)) { mutableMapOf() }[type] = resultSet.getInt(3)
                }
                return stats
            }
        }
    }

    fun hasWonSince(uuid: UUID, since: Long): Boolean {
        database.getConnection().use { connection ->
            connection.prepareStatement("SELECT 1 FROM stat_events WHERE uuid = ? AND type = 'WIN' AND timestamp >= ? LIMIT 1").use { statement ->
                statement.setString(1, uuid.toString())
                statement.setLong(2, since)

                return statement.executeQuery().next()
            }
        }
    }

    fun unlock(uuid: UUID, achievement: Achievement): Boolean {
        database.getConnection().use { connection ->
            connection.prepareStatement("INSERT INTO achievements (uuid, achievement, unlocked_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING").use { statement ->
                statement.setString(1, uuid.toString())
                statement.setString(2, achievement.name)
                statement.setLong(3, System.currentTimeMillis())

                return statement.executeUpdate() > 0
            }
        }
    }

    fun getAchievements(uuid: UUID): Map<Achievement, Long> {
        database.getConnection().use { connection ->
            connection.prepareStatement("SELECT achievement, unlocked_at FROM achievements WHERE uuid = ?").use { statement ->
                statement.setString(1, uuid.toString())

                val resultSet = statement.executeQuery()
                return buildMap {
                    while (resultSet.next()) {
                        val achievement = runCatching { Achievement.valueOf(resultSet.getString(1)) }.getOrNull() ?: continue
                        put(achievement, resultSet.getLong(2))
                    }
                }
            }
        }
    }
}
