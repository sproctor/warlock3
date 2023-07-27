package cc.warlock.warlock3.core.compass

enum class DirectionType(val value: String, val abbreviation: String) {
    North("north", "n"),
    Northeast("northeast", "ne"),
    East("east", "e"),
    Southeast("southeast", "se"),
    South("south", "s"),
    Southwest("southwest", "sw"),
    West("west", "w"),
    Northwest("northwest", "nw"),
    Up("up", "up"),
    Down("down", "down"),
    Out("out", "out");

    companion object {
        fun fromAbbreviation(abbreviation: String): DirectionType? {
            entries.forEach {
                if (it.abbreviation == abbreviation) {
                    return it
                }
            }
            return null
        }
    }
}
