package cc.warlock.warlock3.core.script.wsl

import cc.warlock.warlock3.core.client.WarlockClient
import java.math.BigDecimal

class WslProperties(
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

    override fun toMap(): Map<String, WslValue> {
        return client.properties.value.mapValues { WslString(it.value) }
    }

    override fun isMap(): Boolean {
        return true
    }
}