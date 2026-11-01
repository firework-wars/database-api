package xyz.fireworkwars.data

/** Every achievement on the network: [game] is the game it's for ("FWW" or "LR"), or null when any game counts. */
enum class Achievement(val title: String, val description: String, val xp: Int, val game: String? = null) {
    FIRST_GAME("First Steps", "Play your first game.", 50),
    FIRST_WIN("Victory", "Win your first game.", 100),
    WINS_10("Seasoned", "Win 10 games.", 250),
    WINS_100("Champion", "Win 100 games.", 1000),
    FIRST_KILL("First Blood", "Get your first kill.", 50),
    KILLS_100("Hunter", "Get 100 kills.", 500),
    KILLS_1000("Warlord", "Get 1,000 kills.", 2000),
    WINSTREAK_5("On Fire", "Win 5 games in a row.", 500),
    SUPPLY_DROP("Special Delivery", "Open a supply drop.", 50),
    LEVEL_10("Rising Star", "Reach level 10.", 250),
    LEVEL_25("Veteran", "Reach level 25.", 500),
    LEVEL_50("Legend", "Reach level 50.", 1000),

    FWW_FLAWLESS("Flawless", "Win a game of Firework Wars without dying.", 250, "FWW"),
    FWW_RAMPAGE("Rampage", "Get 5 kills in one game of Firework Wars.", 250, "FWW"),
    FWW_REFILL("Restocked", "Refill a chest with a refill key.", 50, "FWW"),

    LR_WIN("Above the Lava", "Win a game of Lava Rising.", 250, "LR"),
    LR_RAMPAGE("Hot Streak", "Get 3 kills in one game of Lava Rising.", 250, "LR"),
    LR_NETHER("Into the Nether", "Go through a Nether portal in Lava Rising.", 50, "LR")
}
