package warlockfe.warlock3.scripting.wsl

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlin.time.Clock

class WslTimer : WslNumeric() {
    private val startTime = Clock.System.now().toEpochMilliseconds()

    override fun toNumber(): BigDecimal {
        return ((Clock.System.now().toEpochMilliseconds() - startTime) / 1000L).toBigDecimal()
    }
}