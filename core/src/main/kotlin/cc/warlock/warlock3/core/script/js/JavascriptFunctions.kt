package cc.warlock.warlock3.core.script.js

import org.mozilla.javascript.annotations.JSFunction
import java.util.concurrent.TimeUnit

object JavascriptFunctions {
    @JvmStatic
    @JSFunction
    fun pause(duration: Any?) {
        when (duration) {
            is Number -> Thread.sleep((duration.toDouble() * TimeUnit.SECONDS.toMillis(1)).toLong())
            else -> Thread.sleep(1000)
        }
    }

    @JvmStatic
    @JSFunction
    fun exit() {
        throw StopException()
    }
}