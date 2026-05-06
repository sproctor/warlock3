package warlockfe.warlock3.scripting.wsl

abstract class WslNumeric : WslValue {
    override fun toBoolean(): Boolean = throw WslRuntimeException("Attempted to use number as a boolean")

    override fun getProperty(key: String): WslValue = WslNull

    override fun setProperty(
        key: String,
        value: WslValue,
    ) {}

    override fun toString(): String = toNumber().toPlainString()

    override fun equals(other: Any?): Boolean =
        when {
            other !is WslValue -> false
            other.isBoolean() -> toBoolean() == other.toBoolean()
            other.isNumeric() -> toNumber() == other.toNumber()
            else -> toString().equals(other = other.toString(), ignoreCase = true)
        }

    override fun hashCode(): Int = toNumber().hashCode()

    override fun isNumeric(): Boolean = true

    override fun isBoolean(): Boolean = false

    override fun isMap(): Boolean = false
}
