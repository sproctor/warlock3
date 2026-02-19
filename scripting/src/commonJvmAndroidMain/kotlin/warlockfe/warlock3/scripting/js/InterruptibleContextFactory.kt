package warlockfe.warlock3.scripting.js

import co.touchlab.kermit.Logger
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory

class InterruptibleContextFactory(
    private val runningScripts: List<JsInstance>,
) : ContextFactory() {

    private val logger = Logger.withTag("InterruptibleContextFactory")

    override fun makeContext(): Context {
        val cx = super.makeContext()
        cx.instructionObserverThreshold = 10_000
        // FIXME: it's possible the following line is no longer needed
        cx.isInterpretedMode = true
        return cx
    }

    override fun observeInstructionCount(cx: Context?, instructionCount: Int) {
        if (cx == null)
            return
        logger.d { "$instructionCount instructions executed." }
        if (Thread.interrupted()) {
            val instance = getInstance(cx)
            instance.checkStatus()
        }
    }

    fun getInstance(cx: Context): JsInstance {
        for (instance in runningScripts) {
            if (instance.context == cx) {
                return instance
            }
        }
        throw IllegalArgumentException("Context does not have an associated instance")
    }
}

class StopException : Exception()
