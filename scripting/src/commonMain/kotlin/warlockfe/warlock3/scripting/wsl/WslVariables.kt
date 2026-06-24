package warlockfe.warlock3.scripting.wsl

import com.ionspin.kotlin.bignum.decimal.BigDecimal

class WslVariables(
    private val context: WslContext,
) : WslValue {
    override fun toString(): String = ""

    override fun toBoolean(): Boolean = false

    override fun toNumber(): BigDecimal = BigDecimal.ZERO

    override fun isNumeric(): Boolean = false

    override fun isBoolean(): Boolean = false

    override fun getProperty(key: String): WslValue = context.lookupVariable(key) ?: WslNull

    override fun setProperty(
        key: String,
        value: WslValue,
    ) {
        context.setScriptVariableRaw(key, value)
    }

    override fun isMap(): Boolean = true
}
