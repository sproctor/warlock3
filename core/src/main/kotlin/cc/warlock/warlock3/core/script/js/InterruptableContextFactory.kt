package cc.warlock.warlock3.core.script.js

import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory

class InterruptableContextFactory : ContextFactory() {

    override fun makeContext(): Context {
        val cx = super.makeContext()
        cx.instructionObserverThreshold = 10_000
        cx.optimizationLevel = -1
        return cx
    }

    override fun observeInstructionCount(cx: Context?, instructionCount: Int) {
        println("$instructionCount instructions executed.")
        if (Thread.currentThread().isInterrupted) {
            throw StopException()
        }
    }
}

class StopException : Exception()