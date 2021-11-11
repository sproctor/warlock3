package cc.warlock.warlock3.core.script.wsl

import cc.warlock.warlock3.core.client.WarlockClient
import java.math.BigDecimal

class WslComponents(
    private val client: WarlockClient
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

    override fun getProperty(key: String): WslValue {
        return client.components.value[key]?.toPlainString()?.let { WslString(it) } ?: WslNull
    }

    override fun setProperty(key: String, value: WslValue) {
        throw WslRuntimeException("Cannot set properties of components")
    }

    override fun isMap(): Boolean {
        return true
    }
}