package warlockfe.warlock3.scripting.wsl

import java.math.BigDecimal

class WslTimer : WslNumeric() {
    private val startTime = System.currentTimeMillis()

    override fun toNumber(): BigDecimal {
        return ((System.currentTimeMillis() - startTime) / 1000L).toBigDecimal()
    }
}