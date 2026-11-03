package xyz.fireworkwars.data

import java.sql.Connection
import java.sql.PreparedStatement
import java.util.UUID

enum class Privacy { EVERYONE, FRIENDS, NOBODY }

enum class SocialAction(val column: String) {
    FRIEND_REQUESTS("friend_requests"),
    MESSAGES("messages"),
    PARTY_INVITES("party_invites")
}

data class SocialSettings(
    val friendRequests: Privacy = Privacy.EVERYONE,
    val messages: Privacy = Privacy.EVERYONE,
    val partyInvites: Privacy = Privacy.FRIENDS
) {
    operator fun get(action: SocialAction): Privacy = when (action) {
        SocialAction.FRIEND_REQUESTS -> friendRequests
        SocialAction.MESSAGES -> messages
        SocialAction.PARTY_INVITES -> partyInvites
    }
}

@Suppress("unused")
class Social(private val database: Database) {

    fun getFriends(uuid: UUID): List<UUID> =
        queryUuids("SELECT friend FROM friends WHERE uuid = ? ORDER BY since", uuid)

    fun areFriends(a: UUID, b: UUID): Boolean =
        exists("SELECT 1 FROM friends WHERE uuid = ? AND friend = ?", a, b)

    fun addFriendship(a: UUID, b: UUID) {
        val now = System.currentTimeMillis()

        transaction { connection ->
            listOf(a to b, b to a).forEach { (uuid, friend) ->
                connection.prepareStatement("INSERT INTO friends (uuid, friend, since) VALUES (?, ?, ?) ON CONFLICT DO NOTHING").use {
                    it.setString(1, uuid.toString()); it.setString(2, friend.toString()); it.setLong(3, now)
                    it.executeUpdate()
                }
            }
            deleteRequests(connection, a, b)
        }
    }

    fun markUnannounced(uuid: UUID, friend: UUID) {
        update("UPDATE friends SET unannounced = TRUE WHERE uuid = ? AND friend = ?") {
            it.setString(1, uuid.toString()); it.setString(2, friend.toString())
        }
    }

    fun takeUnannounced(uuid: UUID): List<UUID> = queryUuids("""
        WITH announced AS (UPDATE friends SET unannounced = FALSE WHERE uuid = ? AND unannounced RETURNING friend, since)
        SELECT friend FROM announced ORDER BY since
    """.trimIndent(), uuid)

    fun removeFriendship(a: UUID, b: UUID): Boolean = transaction { connection ->
        connection.prepareStatement("DELETE FROM friends WHERE (uuid = ? AND friend = ?) OR (uuid = ? AND friend = ?)").use {
            it.setString(1, a.toString()); it.setString(2, b.toString())
            it.setString(3, b.toString()); it.setString(4, a.toString())
            it.executeUpdate() > 0
        }
    }

    fun hasRequest(sender: UUID, target: UUID): Boolean =
        exists("SELECT 1 FROM friend_requests WHERE sender = ? AND target = ?", sender, target)

    fun addRequest(sender: UUID, target: UUID) {
        update("INSERT INTO friend_requests (sender, target, sent_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING") {
            it.setString(1, sender.toString()); it.setString(2, target.toString()); it.setLong(3, System.currentTimeMillis())
        }
    }

    fun removeRequest(sender: UUID, target: UUID): Boolean =
        update("DELETE FROM friend_requests WHERE sender = ? AND target = ?") {
            it.setString(1, sender.toString()); it.setString(2, target.toString())
        } > 0

    fun getIncomingRequests(uuid: UUID): List<UUID> =
        queryUuids("SELECT sender FROM friend_requests WHERE target = ? ORDER BY sent_at", uuid)

    fun getOutgoingRequests(uuid: UUID): List<UUID> =
        queryUuids("SELECT target FROM friend_requests WHERE sender = ? ORDER BY sent_at", uuid)

    fun hasBlocked(uuid: UUID, other: UUID): Boolean =
        exists("SELECT 1 FROM blocks WHERE uuid = ? AND blocked = ?", uuid, other)

    fun isBlockedEitherWay(a: UUID, b: UUID): Boolean = hasBlocked(a, b) || hasBlocked(b, a)

    fun getBlocked(uuid: UUID): List<UUID> =
        queryUuids("SELECT blocked FROM blocks WHERE uuid = ? ORDER BY since", uuid)

    fun block(uuid: UUID, blocked: UUID): Boolean = transaction { connection ->
        val added = connection.prepareStatement("INSERT INTO blocks (uuid, blocked, since) VALUES (?, ?, ?) ON CONFLICT DO NOTHING").use {
            it.setString(1, uuid.toString()); it.setString(2, blocked.toString()); it.setLong(3, System.currentTimeMillis())
            it.executeUpdate() > 0
        }
        connection.prepareStatement("DELETE FROM friends WHERE (uuid = ? AND friend = ?) OR (uuid = ? AND friend = ?)").use {
            it.setString(1, uuid.toString()); it.setString(2, blocked.toString())
            it.setString(3, blocked.toString()); it.setString(4, uuid.toString())
            it.executeUpdate()
        }
        deleteRequests(connection, uuid, blocked)
        added
    }

    fun unblock(uuid: UUID, blocked: UUID): Boolean =
        update("DELETE FROM blocks WHERE uuid = ? AND blocked = ?") {
            it.setString(1, uuid.toString()); it.setString(2, blocked.toString())
        } > 0

    fun getSettings(uuid: UUID): SocialSettings {
        database.getConnection().use { connection ->
            connection.prepareStatement("SELECT * FROM social_settings WHERE uuid = ?").use { statement ->
                statement.setString(1, uuid.toString())

                val resultSet = statement.executeQuery()
                if (!resultSet.next()) return SocialSettings()

                return SocialSettings(
                    friendRequests = Privacy.valueOf(resultSet.getString("friend_requests")),
                    messages = Privacy.valueOf(resultSet.getString("messages")),
                    partyInvites = Privacy.valueOf(resultSet.getString("party_invites"))
                )
            }
        }
    }

    fun setPrivacy(uuid: UUID, action: SocialAction, privacy: Privacy) {
        update("""
            INSERT INTO social_settings (uuid, ${action.column}) VALUES (?, ?)
            ON CONFLICT (uuid) DO UPDATE SET ${action.column} = EXCLUDED.${action.column}
        """.trimIndent()) {
            it.setString(1, uuid.toString()); it.setString(2, privacy.name)
        }
    }

    private fun deleteRequests(connection: Connection, a: UUID, b: UUID) {
        connection.prepareStatement("DELETE FROM friend_requests WHERE (sender = ? AND target = ?) OR (sender = ? AND target = ?)").use {
            it.setString(1, a.toString()); it.setString(2, b.toString())
            it.setString(3, b.toString()); it.setString(4, a.toString())
            it.executeUpdate()
        }
    }

    private fun queryUuids(sql: String, uuid: UUID): List<UUID> {
        database.getConnection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, uuid.toString())

                val resultSet = statement.executeQuery()
                return buildList { while (resultSet.next()) add(UUID.fromString(resultSet.getString(1))) }
            }
        }
    }

    private fun exists(sql: String, a: UUID, b: UUID): Boolean {
        database.getConnection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, a.toString())
                statement.setString(2, b.toString())

                return statement.executeQuery().next()
            }
        }
    }

    private fun update(sql: String, bind: (PreparedStatement) -> Unit): Int {
        database.getConnection().use { connection ->
            connection.prepareStatement(sql).use { statement ->
                bind(statement)
                return statement.executeUpdate()
            }
        }
    }

    private fun <T> transaction(block: (Connection) -> T): T {
        database.getConnection().use { connection ->
            connection.autoCommit = false
            try {
                val result = block(connection)
                connection.commit()
                return result
            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }
    }
}
