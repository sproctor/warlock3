package warlockfe.warlock3.scripting.wsl

import com.ionspin.kotlin.bignum.decimal.BigDecimal

class WslVariables(
    private val context: WslContext
) : WslValue {
    override fun toString(): String {
        return ""
    }

    override fun toBoolean(): Boolean {
        return false
    }

    override fun toNumber(): BigDecimal {
        return BigDecimal.ZERO
    }

    override fun isNumeric(): Boolean {
        return false
    }

    override fun isBoolean(): Boolean {
        return false
    }

    override fun getProperty(key: String): WslValue {
        return context.lookupVariable(key) ?: WslNull
    }

    override fun setProperty(key: String, value: WslValue) {
        context.setScriptVariableRaw(key, value)
    }

    override fun isMap(): Boolean {
        return true
    }
}