package xyz.fireworkwars.data

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionSettings(
    val redisHost: String,
    val redisPort: Int,

    val databaseHost: String,
    val databasePort: Int,
    val databaseName: String,
    val databaseUser: String,
    val databasePassword: String,
)
