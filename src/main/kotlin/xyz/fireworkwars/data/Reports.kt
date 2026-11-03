package xyz.fireworkwars.data

import java.util.UUID

enum class ReportCategory(val title: String) {
    CHEATING("Cheating"),
    CHAT("Chat"),
    TEAMING("Teaming"),
    NAME_OR_SKIN("Name or skin"),
    OTHER("Other")
}

data class Report(
    val id: Long,
    val reporter: UUID,
    val target: UUID,
    val category: ReportCategory,
    val server: String?,
    val createdAt: Long
)

@Suppress("unused")
class Reports(private val database: Database) {

    fun create(reporter: UUID, target: UUID, category: ReportCategory, server: String?): Long {
        database.getConnection().use { connection ->
            connection.prepareStatement("INSERT INTO reports (reporter, target, category, server, created_at) VALUES (?, ?, ?, ?, ?) RETURNING id").use { statement ->
                statement.setString(1, reporter.toString())
                statement.setString(2, target.toString())
                statement.setString(3, category.name)
                statement.setString(4, server)
                statement.setLong(5, System.currentTimeMillis())

                val resultSet = statement.executeQuery()
                resultSet.next()
                return resultSet.getLong(1)
            }
        }
    }

    fun hasOpen(reporter: UUID, target: UUID): Boolean {
        database.getConnection().use { connection ->
            connection.prepareStatement("SELECT 1 FROM reports WHERE reporter = ? AND target = ? AND closed_at IS NULL").use { statement ->
                statement.setString(1, reporter.toString())
                statement.setString(2, target.toString())

                return statement.executeQuery().next()
            }
        }
    }

    fun getOpen(): List<Report> {
        database.getConnection().use { connection ->
            connection.prepareStatement("SELECT * FROM reports WHERE closed_at IS NULL ORDER BY created_at").use { statement ->
                val resultSet = statement.executeQuery()

                return buildList {
                    while (resultSet.next()) {
                        add(Report(
                            id = resultSet.getLong("id"),
                            reporter = UUID.fromString(resultSet.getString("reporter")),
                            target = UUID.fromString(resultSet.getString("target")),
                            category = runCatching { ReportCategory.valueOf(resultSet.getString("category")) }.getOrDefault(ReportCategory.OTHER),
                            server = resultSet.getString("server"),
                            createdAt = resultSet.getLong("created_at")
                        ))
                    }
                }
            }
        }
    }

    fun close(id: Long, staff: UUID?): Int {
        database.getConnection().use { connection ->
            connection.prepareStatement("""
                UPDATE reports SET closed_at = ?, closed_by = ?
                WHERE closed_at IS NULL AND target = (SELECT target FROM reports WHERE id = ?)
            """.trimIndent()).use { statement ->
                statement.setLong(1, System.currentTimeMillis())
                statement.setString(2, staff?.toString())
                statement.setLong(3, id)

                return statement.executeUpdate()
            }
        }
    }
}
