package warlockfe.warlock3.core.client

@JvmInline
value class Percentage(val value: Int) {
    init {
        require(value >= 0)
        require(value <= 100)
    }
}