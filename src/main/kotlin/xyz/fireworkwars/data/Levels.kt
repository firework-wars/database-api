package xyz.fireworkwars.data

/** Network levels: everyone starts at level 1, and each level takes 100 XP more than the last (500 XP from 1 to 2). */
object Levels {

    fun xpToNext(level: Int): Long = 400L + 100L * level

    fun levelOf(xp: Long): Int {
        var level = 1
        var left = xp

        while (left >= xpToNext(level)) {
            left -= xpToNext(level)
            level++
        }
        return level
    }

    /** How far into their current level [xp] puts a player. */
    fun xpIntoLevel(xp: Long): Long {
        var level = 1
        var left = xp

        while (left >= xpToNext(level)) {
            left -= xpToNext(level)
            level++
        }
        return left
    }
}

/** What a finished game is worth, before achievements. */
enum class XpReward(val amount: Int) {
    GAME(25),
    KILL(25),
    WIN(200),
    DRAW(75),
    FIRST_WIN_OF_DAY(250)
}
