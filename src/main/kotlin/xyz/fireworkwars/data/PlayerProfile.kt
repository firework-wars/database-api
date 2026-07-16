package xyz.fireworkwars.data

import net.kyori.adventure.text.Component
import java.util.UUID

data class PlayerProfile(
    val uuid: UUID,
    var rank: Rank = Rank.NONE,
    val firstJoin: Long,
    var lastSeen: Long,
    var playTime: Long = 0,
    val names: MutableList<String> = mutableListOf(),
    val clientBrands: MutableSet<String> = mutableSetOf(),
    val ips: MutableSet<String> = mutableSetOf()
) {
    fun currentName(): String = names.lastOrNull() ?: "Unknown"

    fun formattedName(): Component = rank.format(currentName())
}
