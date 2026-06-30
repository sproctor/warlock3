package warlockfe.warlock3.scripting.wsl

class WslVariables(
    private val context: WslContext,
) : WslValue {
    override fun toText(): String = ""

    override fun toString(): String = ""

    override fun toBoolean(): Boolean = false

    override fun toNumber(): Double = 0.0

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
