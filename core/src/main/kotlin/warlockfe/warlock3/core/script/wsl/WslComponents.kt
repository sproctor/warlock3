package warlockfe.warlock3.core.script.wsl

import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.util.getIgnoringCase
import java.math.BigDecimal

class WslComponents(
    private val client: WarlockClient
) : WslValue {
    override fun toString(): String {
        return client.components.value.toString()
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
        return client.components.value.getIgnoringCase(key)?.let { WslString(it.toString()) } ?: WslNull
    }

    override fun setProperty(key: String, value: WslValue) {
        throw WslRuntimeException("Cannot set properties of components")
    }

    override fun isMap(): Boolean {
        return true
    }
}