package warlockfe.warlock3.scripting.wsl

abstract class WslNumeric : WslValue {
    override fun toBoolean(): Boolean {
        throw WslRuntimeException("Attempted to use number as a boolean")
    }

    override fun getProperty(key: String): WslValue {
        return WslNull
    }

    override fun setProperty(key: String, value: WslValue) {}

    override fun toString(): String {
        return toNumber().toPlainString()
    }

    override fun equals(other: Any?): Boolean {
        return when {
            other !is WslValue -> false
            other.isBoolean() -> toBoolean() == other.toBoolean()
            other.isNumeric() -> toNumber() == other.toNumber()
            else -> toString().equals(other = other.toString(), ignoreCase = true)
        }
    }

    override fun hashCode(): Int {
        return toNumber().hashCode()
    }

    override fun isNumeric(): Boolean {
        return true
    }

    override fun isBoolean(): Boolean {
        return false
    }

    override fun isMap(): Boolean {
        return false
    }
}