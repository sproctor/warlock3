package warlockfe.warlock3.scripting.wsl

import kotlin.time.Clock

class WslTimer : WslNumeric() {
    private val startTime = Clock.System.now()

    override fun toNumber(): Double {
        val duration = Clock.System.now() - startTime
        return duration.inWholeSeconds.toDouble()
    }
}
