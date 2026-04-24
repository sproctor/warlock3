package warlockfe.warlock3.scripting.wsl

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlin.time.Clock

class WslTimer : WslNumeric() {
    private val startTime = Clock.System.now()

    override fun toNumber(): BigDecimal {
        val duration = Clock.System.now() - startTime
        return duration.inWholeSeconds.toBigDecimal()
    }
}
