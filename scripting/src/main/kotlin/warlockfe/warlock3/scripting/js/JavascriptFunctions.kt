package warlockfe.warlock3.scripting.js

import org.mozilla.javascript.annotations.JSFunction
import java.util.concurrent.TimeUnit

object JavascriptFunctions {
    @JvmStatic
    @JSFunction
    fun pause(duration: Any?) {
        if (!Thread.currentThread().isInterrupted) {
            try {
                when (duration) {
                    is Number -> Thread.sleep((duration.toDouble() * TimeUnit.SECONDS.toMillis(1)).toLong())
                    else -> Thread.sleep(1000)
                }
            } catch (e: InterruptedException) {
                // nothing to do
            }
        }
    }

    @JvmStatic
    @JSFunction
    fun exit() {
        throw StopException()
    }
}