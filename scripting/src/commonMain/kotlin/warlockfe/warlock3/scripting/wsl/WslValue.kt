package warlockfe.warlock3.scripting.wsl

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import warlockfe.warlock3.scripting.util.toBigDecimalOrNull

interface WslValue {
    fun toBoolean(): Boolean

    fun toNumber(): BigDecimal

    fun isNumeric(): Boolean

    fun isBoolean(): Boolean

    fun getProperty(key: String): WslValue

    fun setProperty(
        key: String,
        value: WslValue,
    )

    fun isMap(): Boolean

    fun isNull(): Boolean = false

    fun compareWith(
        operator: WslComparisonOperator,
        other: WslValue,
    ): Boolean {
        if (isNumeric() && other.isNumeric()) {
            return compare(operator, toNumber(), other.toNumber())
        }
        return compare(operator, toString(), other.toString())
    }
}

class WslBoolean(
    val value: Boolean,
) : WslValue {
    override fun isBoolean(): Boolean = true

    override fun toBoolean(): Boolean = value

    override fun toNumber(): BigDecimal = throw WslRuntimeException("Boolean cannot be used as a number")

    override fun getProperty(key: String): WslValue = WslNull

    override fun setProperty(
        key: String,
        value: WslValue,
    ) { }

    override fun toString(): String = if (value) "true" else "false"

    override fun equals(other: Any?): Boolean =
        when (other) {
            is WslBoolean -> value == other.toBoolean()
            else -> false
        }

    override fun hashCode(): Int = value.hashCode()

    override fun isNumeric(): Boolean = false

    override fun isMap(): Boolean = false
}

class WslString(
    val value: String,
) : WslValue {
    override fun isBoolean(): Boolean = false

    override fun toBoolean(): Boolean =
        when (value.lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw WslRuntimeException("String that is not \"true\" or \"false\" cannot be used as a boolean")
        }

    override fun toNumber(): BigDecimal {
        if (value.isBlank()) return BigDecimal.ZERO
        return value.toBigDecimalOrNull()
            ?: throw WslRuntimeException("String \"$value\" cannot be converted to a number.")
    }

    override fun toString(): String = value

    override fun getProperty(key: String): WslValue {
        val index = key.toIntOrNull()
        return if (index != null) {
            value.getOrNull(index)?.let { WslString(it.toString()) } ?: WslNull
        } else {
            WslNull
        }
    }

    override fun setProperty(
        key: String,
        value: WslValue,
    ) {}

    override fun equals(other: Any?): Boolean =
        try {
            when {
                other !is WslValue -> false
                other.isBoolean() -> toBoolean() == other.toBoolean()
                other.isNumeric() -> toNumber() == other.toNumber()
                else -> value.equals(other = other.toString(), ignoreCase = true)
            }
        } catch (_: WslRuntimeException) {
            false
        }

    override fun hashCode(): Int = value.hashCode()

    override fun isNumeric(): Boolean = value.toBigDecimalOrNull() != null

    override fun isMap(): Boolean = false
}

class WslNumber(
    private val value: BigDecimal,
) : WslNumeric() {
    override fun toNumber(): BigDecimal = value
}

object WslNull : WslValue {
    override fun equals(other: Any?): Boolean =
        when (other) {
            WslNull -> true
            else -> false
        }

    override fun toString(): String = ""

    override fun toBoolean(): Boolean = false

    override fun toNumber(): BigDecimal = throw WslRuntimeException("Cannot convert null to number")

    override fun getProperty(key: String): WslValue = WslNull

    override fun setProperty(
        key: String,
        value: WslValue,
    ) {}

    override fun isNumeric(): Boolean = false

    override fun isBoolean(): Boolean = false

    override fun isMap(): Boolean = false

    override fun isNull(): Boolean = true
}

class WslMap(
    initialValues: Map<String, WslValue>,
) : WslValue {
    private val values = initialValues.toMutableMap()

    override fun toBoolean(): Boolean = false

    override fun toNumber(): BigDecimal = BigDecimal.ZERO

    override fun isNumeric(): Boolean = false

    override fun isBoolean(): Boolean = false

    override fun getProperty(key: String): WslValue = values[key] ?: WslNull

    override fun setProperty(
        key: String,
        value: WslValue,
    ) {
        values[key] = value
    }

    override fun toString(): String = values.toString()

    override fun isMap(): Boolean = true
}

private fun <T> compare(
    operator: WslComparisonOperator,
    value1: Comparable<T>,
    value2: T,
): Boolean =
    when (operator) {
        WslComparisonOperator.GT -> value1 > value2
        WslComparisonOperator.LT -> value1 < value2
        WslComparisonOperator.GTE -> value1 >= value2
        WslComparisonOperator.LTE -> value1 <= value2
    }
