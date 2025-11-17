package warlockfe.warlock3.scripting.wsl

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import warlockfe.warlock3.scripting.util.toBigDecimalOrNull

class WslVariable(
    private val getter: () -> Any?
) : WslValue {

    override fun toBoolean(): Boolean {
        val value = getter()
        return value as? Boolean ?: value.toString().toBoolean()
    }

    override fun toNumber(): BigDecimal {
        return when (val value = getter()) {
            is BigDecimal -> value
            is Int -> value.toBigDecimal()
            is Long -> value.toBigDecimal()
            is Float -> value.toBigDecimal()
            is Double -> value.toBigDecimal()
            else -> toString().toBigDecimalOrNull() ?: BigDecimal.ZERO
        }
    }

    override fun isNumeric(): Boolean {
        return getter() is Number
    }

    override fun isBoolean(): Boolean {
        return getter() is Boolean
    }

    override fun getProperty(key: String): WslValue {
        val value = getter()
        if (value is Map<*, *>) {
            return WslVariable { value[key] }
        } else {
            return WslNull
        }
    }

    override fun setProperty(key: String, value: WslValue) {
        val map = getter() as? MutableMap<String, String>
        if (map != null) {
            map[key] = value.toString()
        }
    }

    override fun isMap(): Boolean {
        return getter() is Map<*, *>
    }

    override fun isNull(): Boolean {
        return getter() == null
    }
}
