package cc.warlock.warlock3.core.script.js

import cc.warlock.warlock3.core.script.WarlockScriptEngineRegistry
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory

class InterruptableContextFactory(
    private val scriptEngineRegistry: WarlockScriptEngineRegistry
) : ContextFactory() {

    override fun makeContext(): Context {
        val cx = super.makeContext()
        cx.instructionObserverThreshold = 10_000
        cx.optimizationLevel = -1
        return cx
    }

    override fun observeInstructionCount(cx: Context?, instructionCount: Int) {
        if (cx == null)
            return
        println("$instructionCount instructions executed.")
        if (Thread.interrupted()) {
            val instance = getInstance(cx)
            instance.checkStatus()
        }
    }

    fun getInstance(cx: Context): JsInstance {
        for (instance in scriptEngineRegistry.runningScripts) {
            if (instance is JsInstance && instance.context == cx) {
                return instance
            }
        }
        throw IllegalArgumentException("Context does not have an associated instance")
    }
}

class StopException : Exception()