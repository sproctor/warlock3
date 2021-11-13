package cc.warlock.warlock3.core.script.wsl

import java.math.BigDecimal

interface WslValue {
    fun toBoolean(): Boolean
    fun toNumber(): BigDecimal
    fun isNumeric(): Boolean
    fun getProperty(key: String): WslValue
    fun setProperty(key: String, value: WslValue)
    fun isMap(): Boolean
    fun isNull(): Boolean = false

    fun compareWith(operator: WslComparisonOperator, other: WslValue): Boolean {
        if (isNumeric() && other.isNumeric())
            return compare(operator, toNumber(), other.toNumber())
        return compare(operator, toString(), other.toString())
    }
}

data class WslBoolean(val value: Boolean) : WslValue {
    override fun toBoolean(): Boolean {
        return value
    }

    override fun toNumber(): BigDecimal {
        throw WslRuntimeException("Boolean cannot be used as a number")
    }

    override fun getProperty(key: String): WslValue {
        return WslNull
    }

    override fun setProperty(key: String, value: WslValue) { }

    override fun toString(): String {
        return if (value) "true" else "false"
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is WslBoolean -> value == other.toBoolean()
            else -> false
        }
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    override fun isNumeric(): Boolean {
        return false
    }

    override fun isMap(): Boolean {
        return false
    }
}

data class WslString(val value: String) : WslValue {
    override fun toBoolean(): Boolean {
        return when (value.lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw WslRuntimeException("String that is not \"true\" or \"false\" cannot be used as a boolean")
        }
    }

    override fun toNumber(): BigDecimal {
        if (value.isBlank()) return BigDecimal.ZERO
        return value.toBigDecimalOrNull()
            ?: throw WslRuntimeException("String \"$value\" cannot be converted to a number.")
    }

    override fun toString(): String {
        return value
    }

    override fun getProperty(key: String): WslValue {
        val index = key.toIntOrNull()
        return if (index != null) {
            value.getOrNull(index)?.let { WslString(it.toString()) } ?: WslNull
        } else {
            WslNull
        }
    }

    override fun setProperty(key: String, value: WslValue) {}

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is WslBoolean -> value.toBoolean() == other.value
            is WslString -> value.equals(other = other.value, ignoreCase = true)
            is WslNumber -> value.toBigDecimal() == other.value
            else -> false
        }
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    override fun isNumeric(): Boolean {
        return value.toBigDecimalOrNull() != null
    }

    override fun isMap(): Boolean {
        return false
    }
}

data class WslNumber(val value: BigDecimal) : WslValue {
    override fun toBoolean(): Boolean {
        throw WslRuntimeException("Attempted to use number as a boolean")
    }

    override fun toNumber(): BigDecimal {
        return value
    }

    override fun getProperty(key: String): WslValue {
        return WslNull
    }

    override fun setProperty(key: String, value: WslValue) {}

    override fun toString(): String {
        return value.toPlainString()
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is WslBoolean -> toBoolean() == other.value
            is WslString -> value == other.toNumber()
            is WslNumber -> value == other.value
            else -> false
        }
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    override fun isNumeric(): Boolean {
        return true
    }

    override fun isMap(): Boolean {
        return false
    }
}

object WslNull : WslValue {
    override fun equals(other: Any?): Boolean {
        return other == null
    }

    override fun toString(): String {
        return ""
    }

    override fun toBoolean(): Boolean {
        return false
    }

    override fun toNumber(): BigDecimal {
        throw WslRuntimeException("Cannot convert null to number")
    }

    override fun getProperty(key: String): WslValue {
        return WslNull
    }

    override fun setProperty(key: String, value: WslValue) {}

    override fun isNumeric(): Boolean {
        return false
    }

    override fun isMap(): Boolean {
        return false
    }

    override fun isNull(): Boolean = true
}

class WslMap(initialValues: Map<String, WslValue>) : WslValue {
    private val values = initialValues.toMutableMap()

    override fun toBoolean(): Boolean {
        return false
    }

    override fun toNumber(): BigDecimal {
        return BigDecimal.ZERO
    }

    override fun isNumeric(): Boolean {
        return false
    }

    override fun getProperty(key: String): WslValue {
        return values[key] ?: WslNull
    }

    override fun setProperty(key: String, value: WslValue) {
        values[key] = value
    }

    override fun toString(): String {
        return values.toString()
    }

    override fun isMap(): Boolean {
        return true
    }
}

private fun <T> compare(operator: WslComparisonOperator, value1: Comparable<T>, value2: T): Boolean {
    return when (operator) {
        WslComparisonOperator.GT -> value1 > value2
        WslComparisonOperator.LT -> value1 < value2
        WslComparisonOperator.GTE -> value1 >= value2
        WslComparisonOperator.LTE -> value1 <= value2
    }
}