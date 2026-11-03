package xyz.fireworkwars.data

import kotlinx.serialization.json.Json
import java.sql.Connection
import java.util.UUID
import kotlin.math.max

@Suppress("unused")
class PlayerProfiles(private val database: Database) {

    private val json = Json { ignoreUnknownKeys = true }

    fun getOrCreateProfile(uuid: UUID, name: String): PlayerProfile {
        val existing = getProfile(uuid)
        if (existing != null) return existing

        val now = System.currentTimeMillis()
        val profile = PlayerProfile(
            uuid = uuid,
            firstJoin = now,
            lastSeen = now,
            names = mutableListOf(name)
        )

        database.getConnection().use { connection ->
            connection.prepareStatement("""
                INSERT INTO players (uuid, rank, first_join, last_seen, play_time, names, client_brands, ips)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()).use { statement ->
                statement.setString(1, uuid.toString())
                statement.setString(2, profile.rank.name)
                statement.setLong(3, profile.firstJoin)
                statement.setLong(4, profile.lastSeen)
                statement.setLong(5, 0)
                statement.setString(6, json.encodeToString(listOf(name)))
                statement.setString(7, "[]")
                statement.setString(8, "[]")

                statement.executeUpdate()
            }

            connection.prepareStatement("INSERT INTO player_stats (uuid) VALUES (?)").use { statement ->
                statement.setString(1, uuid.toString())

                statement.executeUpdate()
            }
        }

        return profile
    }

    fun getProfile(uuid: UUID): PlayerProfile? {
        database.getConnection().use { connection ->
            connection.prepareStatement("SELECT * FROM players WHERE uuid = ?").use { statement ->
                statement.setString(1, uuid.toString())

                val resultSet = statement.executeQuery()
                if (!resultSet.next()) return null

                return PlayerProfile(
                    uuid = UUID.fromString(resultSet.getString("uuid")),
                    rank = Rank.valueOf(resultSet.getString("rank")),
                    firstJoin = resultSet.getLong("first_join"),
                    lastSeen = resultSet.getLong("last_seen"),
                    playTime = resultSet.getLong("play_time"),
                    names = json.decodeFromString<List<String>>(resultSet.getString("names")).toMutableList(),
                    clientBrands = json.decodeFromString<List<String>>(resultSet.getString("client_brands")).toMutableSet(),
                    ips = json.decodeFromString<List<String>>(resultSet.getString("ips")).toMutableSet()
                )
            }
        }
    }

    fun getRank(uuid: UUID): Rank {
        database.getConnection().use { connection ->
            connection.prepareStatement("SELECT rank FROM players WHERE uuid = ?").use { statement ->
                statement.setString(1, uuid.toString())

                val resultSet = statement.executeQuery()
                if (!resultSet.next()) return Rank.NONE

                return runCatching { Rank.valueOf(resultSet.getString("rank")) }.getOrDefault(Rank.NONE)
            }
        }
    }

    fun setRank(uuid: UUID, rank: Rank): Boolean {
        database.getConnection().use { connection ->
            connection.prepareStatement("UPDATE players SET rank = ? WHERE uuid = ?").use { statement ->
                statement.setString(1, rank.name)
                statement.setString(2, uuid.toString())

                return statement.executeUpdate() > 0
            }
        }
    }

    fun findUuidByName(name: String): UUID? {
        database.getConnection().use { connection ->
            connection.prepareStatement("SELECT uuid FROM players WHERE lower(names::jsonb ->> -1) = lower(?) ORDER BY last_seen DESC LIMIT 1").use { statement ->
                statement.setString(1, name)

                val resultSet = statement.executeQuery()
                return if (resultSet.next()) UUID.fromString(resultSet.getString("uuid")) else null
            }
        }
    }

    fun getNames(uuids: Collection<UUID>): Map<UUID, String> {
        if (uuids.isEmpty()) return emptyMap()

        database.getConnection().use { connection ->
            connection.prepareStatement("SELECT uuid, names::jsonb ->> -1 AS name FROM players WHERE uuid = ANY(?)").use { statement ->
                statement.setArray(1, connection.createArrayOf("text", uuids.map { it.toString() }.toTypedArray()))

                val resultSet = statement.executeQuery()
                return buildMap { while (resultSet.next()) put(UUID.fromString(resultSet.getString("uuid")), resultSet.getString("name")) }
            }
        }
    }

    fun updateLastSeen(uuid: UUID) {
        database.getConnection().use { connection ->
            connection.prepareStatement("UPDATE players SET last_seen = ? WHERE uuid = ?").use { statement ->
                statement.setLong(1, System.currentTimeMillis())
                statement.setString(2, uuid.toString())

                statement.executeUpdate()
            }
        }
    }

    fun updatePlayerName(uuid: UUID, name: String) {
        val profile = getProfile(uuid) ?: return
        if (profile.names.lastOrNull() == name) return

        profile.names.add(name)

        database.getConnection().use { connection ->
            connection.prepareStatement("UPDATE players SET names = ? WHERE uuid = ?").use { statement ->
                statement.setString(1, json.encodeToString(profile.names.toList()))
                statement.setString(2, uuid.toString())

                statement.executeUpdate()
            }
        }
    }

    fun recordPlayerInfo(uuid: UUID, name: String, clientBrand: String?, ip: String?) {
        database.getConnection().use { connection ->
            val (names, brands, ips) = readHistory(connection, uuid) ?: return

            if (names.lastOrNull() != name) names.add(name)
            if (clientBrand != null) brands.add(clientBrand)
            if (ip != null) ips.add(ip)

            connection.prepareStatement("""
                UPDATE players SET last_seen = ?, names = ?, client_brands = ?, ips = ?
                WHERE uuid = ?
            """.trimIndent()).use { statement ->
                statement.setLong(1, System.currentTimeMillis())
                statement.setString(2, json.encodeToString(names.toList()))
                statement.setString(3, json.encodeToString(brands.toList()))
                statement.setString(4, json.encodeToString(ips.toList()))
                statement.setString(5, uuid.toString())

                statement.executeUpdate()
            }
        }
    }

    fun saveGameStats(uuid: UUID, gameType: String, kills: Int, died: Boolean, damage: Double, result: GameResult) {
        val time = System.currentTimeMillis()

        database.getConnection().use { connection ->
            repeat(kills) { recordStatEvent(connection, uuid, StatType.KILL, gameType, time) }

            if (died) recordStatEvent(connection, uuid, StatType.DEATH, gameType, time)

            val resultEvent = when (result) {
                GameResult.WIN -> StatType.WIN
                GameResult.LOSS -> StatType.LOSS
                GameResult.DRAW -> StatType.DRAW
            }
            recordStatEvent(connection, uuid, resultEvent, gameType, time)

            val (winstreak, highest) = loadWinstreak(connection, uuid)
            val newWinstreak = when (result) {
                GameResult.WIN -> winstreak + 1
                GameResult.DRAW -> winstreak
                GameResult.LOSS -> 0
            }

            connection.prepareStatement("""
                UPDATE player_stats SET
                    kills = kills + ?,
                    deaths = deaths + ?,
                    damage = damage + ?,
                    wins = wins + ?,
                    losses = losses + ?,
                    draws = draws + ?,
                    winstreak = ?,
                    highest_winstreak = ?
                WHERE uuid = ?
            """.trimIndent()).use { statement ->
                statement.setInt(1, kills)
                statement.setInt(2, if (died) 1 else 0)
                statement.setDouble(3, damage)
                statement.setInt(4, if (result == GameResult.WIN) 1 else 0)
                statement.setInt(5, if (result == GameResult.LOSS) 1 else 0)
                statement.setInt(6, if (result == GameResult.DRAW) 1 else 0)
                statement.setInt(7, newWinstreak)
                statement.setInt(8, max(newWinstreak, highest))
                statement.setString(9, uuid.toString())

                statement.executeUpdate()
            }
        }
    }

    fun getTopKills(gameType: String, limit: Int, since: Long? = null): List<LeaderboardEntry> {
        return getTopStat(StatType.KILL, gameType, limit, since)
    }

    fun getTopWins(gameType: String, limit: Int, since: Long? = null): List<LeaderboardEntry> {
        return getTopStat(StatType.WIN, gameType, limit, since)
    }

    fun getPosition(uuid: UUID, type: StatType, gameType: String, since: Long? = null): Pair<Int, Int>? {
        val sinceFilter = if (since != null) "AND timestamp >= ?" else ""
        val query = """
            WITH totals AS (
                SELECT uuid, COUNT(*) AS value FROM stat_events
                WHERE type = ? AND game_type = ? $sinceFilter
                GROUP BY uuid
            )
            SELECT value, (SELECT COUNT(*) FROM totals t2 WHERE t2.value > t.value) + 1 AS position
            FROM totals t WHERE uuid = ?
        """.trimIndent()

        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                var index = 1
                statement.setString(index++, type.name)
                statement.setString(index++, gameType)
                if (since != null) statement.setLong(index++, since)
                statement.setString(index, uuid.toString())

                val resultSet = statement.executeQuery()
                return if (resultSet.next()) resultSet.getInt("position") to resultSet.getInt("value") else null
            }
        }
    }

    private fun getTopStat(type: StatType, gameType: String, limit: Int, since: Long?): List<LeaderboardEntry> {
        val entries = mutableListOf<LeaderboardEntry>()

        val sinceFilter = if (since != null) "AND e.timestamp >= ?" else ""
        val query = """
            SELECT e.uuid, p.names, p.rank, COUNT(*) as value
            FROM stat_events e
            JOIN players p ON e.uuid = p.uuid
            WHERE e.type = ? AND e.game_type = ? $sinceFilter
            GROUP BY e.uuid, p.names, p.rank
            ORDER BY value DESC
            LIMIT ?
        """.trimIndent()

        database.getConnection().use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setString(1, type.name)
                statement.setString(2, gameType)

                if (since != null) {
                    statement.setLong(3, since)
                    statement.setInt(4, limit)
                } else {
                    statement.setInt(3, limit)
                }

                val resultSet = statement.executeQuery()

                while (resultSet.next()) {
                    val names = json.decodeFromString<List<String>>(resultSet.getString("names"))

                    entries.add(LeaderboardEntry(
                        uuid = UUID.fromString(resultSet.getString("uuid")),
                        name = names.lastOrNull() ?: "Unknown",
                        rank = Rank.valueOf(resultSet.getString("rank")),
                        value = resultSet.getInt("value")
                    ))
                }
            }
        }

        return entries
    }

    private fun recordStatEvent(connection: Connection, uuid: UUID, type: StatType, gameType: String, timestamp: Long) {
        connection.prepareStatement("INSERT INTO stat_events (uuid, type, game_type, timestamp) VALUES (?, ?, ?, ?)").use { statement ->
            statement.setString(1, uuid.toString())
            statement.setString(2, type.name)
            statement.setString(3, gameType)
            statement.setLong(4, timestamp)

            statement.executeUpdate()
        }
    }

    private fun loadWinstreak(connection: Connection, uuid: UUID): Pair<Int, Int> {
        connection.prepareStatement("SELECT winstreak, highest_winstreak FROM player_stats WHERE uuid = ?").use { statement ->
            statement.setString(1, uuid.toString())

            val resultSet = statement.executeQuery()
            if (resultSet.next()) {
                return Pair(resultSet.getInt("winstreak"), resultSet.getInt("highest_winstreak"))
            }
        }

        return Pair(0, 0)
    }

    private fun readHistory(
        connection: Connection,
        uuid: UUID
    ): Triple<MutableList<String>, MutableSet<String>, MutableSet<String>>? {
        connection.prepareStatement("SELECT names, client_brands, ips FROM players WHERE uuid = ?").use { statement ->
            statement.setString(1, uuid.toString())

            val resultSet = statement.executeQuery()
            if (!resultSet.next()) return null

            return Triple(
                json.decodeFromString<List<String>>(resultSet.getString("names")).toMutableList(),
                json.decodeFromString<List<String>>(resultSet.getString("client_brands")).toMutableSet(),
                json.decodeFromString<List<String>>(resultSet.getString("ips")).toMutableSet()
            )
        }
    }
}
