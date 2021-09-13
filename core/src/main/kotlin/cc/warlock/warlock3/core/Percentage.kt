package cc.warlock.warlock3.core

@JvmInline
value class Percentage(val value: Int) {
    init {
        require(value >= 0)
        require(value <= 100)
    }
}