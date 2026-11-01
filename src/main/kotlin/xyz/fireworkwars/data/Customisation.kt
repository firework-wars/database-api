package xyz.fireworkwars.data

import java.sql.PreparedStatement
import java.util.UUID

/** What plays where a player dies; [level] unlocks it, and GOLD players have them all. */
enum class DeathEffect(val title: String, val level: Int) {
    FIREWORK("Firework", 1),
    LIGHTNING("Lightning", 5),
    BLOOD("Blood", 10),
    SOULS("Souls", 15),
    EXPLOSION("Explosion", 20),
    HEARTS("Hearts", 25),
    ENDER("Ender", 30),
    TOTEM("Totem", 40),
    SQUID("Squid", 50);

    fun isUnlocked(level: Int, rank: Rank): Boolean = rank == Rank.GOLD || level >= this.level
}

/** The parts of a profile a player picks themselves: their death effect and a short status. */
@Suppress("unused")
class Customisation(private val database: Database) {

    fun getDeathEffect(uuid: UUID): DeathEffect {
        val name = queryString("SELECT death_effect FROM player_customisation WHERE uuid = ?", uuid)
        return name?.let { runCatching { DeathEffect.valueOf(it) }.getOrNull() } ?: DeathEffect.FIREWORK
    }

    fun setDeathEffect(uuid: UUID, effect: DeathEffect) {
        update("INSERT INTO player_customisation (uuid, death_effect) VALUES (?, ?) ON CONFLICT (uuid) DO UPDATE SET death_effect = EXCLUDED.death_effect") {
            it.setString(1, uuid.toString()); it.setString(2, effect.name)
        }
    }

    fun getStatus(uuid: UUID): String? = queryString("SELECT status FROM player_customisation WHERE uuid = ?", uuid)

    /** Statuses of the players that have one. */
    fun getStatuses(uuids: Collection<UUID>): Map<UUID, String> {
        if (uuids.isEmpty()) return emptyMap()

        database.getConnection().use { connection ->
            connection.prepareStatement("SELECT uuid, status FROM player_customisation WHERE uuid = ANY(?) AND status IS NOT NULL").use { statement ->
                statement.setArray(1, connection.createArrayOf("text", uuids.map { it.toString() }.toTypedArray()))

                val resultSet = statement.executeQuery()
                return buildMap { while (resultSet.next()) put(UUID.fromString(resultSet.getString(1)), resultSet.getString(2)) }
            }
        }
    }

    /** Sets the status, or clears it with null. */
    fun setStatus(uuid: UUID, status: String?) {
        update("INSERT INTO player_customisation (uuid, status) VALUES (?, ?) ON CONFLICT (uuid) DO UPDATE SET status = EXCLUDED.status") {
            it.setString(1, uuid.toString()); it.setString(2, status)
        }
    }

    private fun queryString(sql: String, uuid: UUID): String? {
        database.getConnection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, uuid.toString())

                val resultSet = statement.executeQuery()
                return if (resultSet.next()) resultSet.getString(1) else null
            }
        }
    }

    private fun update(sql: String, bind: (PreparedStatement) -> Unit) {
        database.getConnection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                bind(statement)
                statement.executeUpdate()
            }
        }
    }

    companion object {
        const val MAX_STATUS_LENGTH = 32
    }
}
