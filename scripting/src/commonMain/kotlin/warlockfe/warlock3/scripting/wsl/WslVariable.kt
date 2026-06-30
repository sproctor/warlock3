package warlockfe.warlock3.scripting.wsl

class WslVariable(
    private val getter: () -> Any?,
) : WslValue {
    override fun toBoolean(): Boolean {
        val value = getter()
        return value as? Boolean ?: value.toString().toBoolean()
    }

    override fun toNumber(): Double =
        when (val value = getter()) {
            is Double -> value
            is Int -> value.toDouble()
            is Long -> value.toDouble()
            is Float -> value.toDouble()
            else -> value?.toString()?.toDoubleOrNull() ?: 0.0
        }

    override fun toText(): String = getter()?.toString() ?: ""

    override fun toString(): String = toText()

    override fun isNumeric(): Boolean = getter() is Number

    override fun isBoolean(): Boolean = getter() is Boolean

    override fun getProperty(key: String): WslValue {
        val value = getter()
        if (value is Map<*, *>) {
            return WslVariable { value[key] }
        } else {
            return WslNull
        }
    }

    override fun setProperty(
        key: String,
        value: WslValue,
    ) {
        @Suppress("UNCHECKED_CAST")
        val map =
            getter()
                ?.takeIf { it is MutableMap<*, *> } as? MutableMap<String, String>
        map?.set(key, value.toText())
    }

    override fun isMap(): Boolean = getter() is Map<*, *>

    override fun isNull(): Boolean = getter() == null
}
