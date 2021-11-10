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

    override fun toMap(): Map<String, WslValue> {
        return client.components.value.mapValues { WslString(it.value.toPlainString()) }
    }
}