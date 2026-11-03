package xyz.fireworkwars.data

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

enum class XpReward(val amount: Int) {
    GAME(25),
    KILL(25),
    WIN(200),
    DRAW(75),
    FIRST_WIN_OF_DAY(250)
}
