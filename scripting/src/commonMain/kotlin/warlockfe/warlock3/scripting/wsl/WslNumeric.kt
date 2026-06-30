package warlockfe.warlock3.scripting.wsl

import kotlin.math.floor

abstract class WslNumeric : WslValue {
    override fun toBoolean(): Boolean = throw WslRuntimeException("Attempted to use number as a boolean")

    override fun getProperty(key: String): WslValue = WslNull

    override fun setProperty(
        key: String,
        value: WslValue,
    ) {}

    override fun toText(): String {
        val n = toNumber()
        return if (n == floor(n) && !n.isInfinite()) {
            n.toLong().toString()
        } else {
            n.toString()
        }
    }

    override fun toString(): String = toText()

    override fun equals(other: Any?): Boolean =
        when {
            other !is WslValue -> false
            other.isBoolean() -> toBoolean() == other.toBoolean()
            other.isNumeric() -> toNumber() == other.toNumber()
            else -> toText().equals(other = other.toText(), ignoreCase = true)
        }

    override fun hashCode(): Int = toNumber().hashCode()

    override fun isNumeric(): Boolean = true

    override fun isBoolean(): Boolean = false

    override fun isMap(): Boolean = false
}
