package xyz.fireworkwars.data

import java.util.UUID

data class LeaderboardEntry(
    val uuid: UUID,
    val name: String,
    val rank: Rank,
    val value: Int
)
