package warlockfe.warlock3.core.client

@JvmInline
value class Percentage(val value: Int) {
    init {
        require(value >= 0)
        require(value <= 100)
    }

    companion object {
        fun fromString(str: String): Percentage {
            return str
                .dropLast(1)
                .toIntOrNull()
                ?.coerceIn(0..100)
                .let { Percentage(it ?: 0) }
        }
    }
}