package xyz.fireworkwars.data

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.MiniMessage

private val mm = MiniMessage.miniMessage()

enum class Rank(val prefix: String? = null, val color: TextColor) {
    NONE(color = NamedTextColor.GRAY),
    GOLD("[✫]", NamedTextColor.GOLD);

    fun format(name: String): Component {
        val text = if (prefix == null) name else "$prefix $name"
        return mm.deserialize(text).color(color)
    }

    fun formattedPrefix(space: Boolean = false): Component {
        if (prefix == null) return Component.empty()

        val component = mm.deserialize(prefix).color(color)
        return if (space) component.appendSpace() else component
    }
}
