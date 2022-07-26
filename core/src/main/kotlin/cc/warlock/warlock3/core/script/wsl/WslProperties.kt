package cc.warlock.warlock3.core.script.wsl

import cc.warlock.warlock3.core.client.WarlockClient
import cc.warlock.warlock3.core.util.getIgnoringCase
import java.math.BigDecimal

class WslProperties(
    private val client: WarlockClient
) : WslValue {
    override fun toString(): String {
        return client.properties.value.toString()
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
        return client.properties.value.getIgnoringCase(key)?.let { WslString(it) } ?: WslNull
    }

    override fun setProperty(key: String, value: WslValue) {
        throw WslRuntimeException("Cannot set properties of properties")
    }

    override fun isMap(): Boolean {
        return true
    }
}