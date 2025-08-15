package warlockfe.warlock3.core.client

@JvmInline
value class Percentage(val value: Int) {
    init {
        require(value >= 0)
        require(value <= 100)
    }

    companion object {
        fun fromString(str: String): Percentage {
            return if (str.endsWith("%")) {
                str.dropLast(1)
            } else {
                str
            }
                .toIntOrNull()
                ?.coerceIn(0..100)
                .let { Percentage(it ?: 0) }
        }
    }
}