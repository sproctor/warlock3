package warlockfe.warlock3.scripting.js

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.annotations.JSFunction
import warlockfe.warlock3.core.client.ClientTextEvent
import warlockfe.warlock3.core.script.ScriptStatus

class MatchList : ScriptableObject() {

    override fun getClassName(): String = "MatchList"

    private val matches: MutableList<JsMatch> = mutableListOf()

    @JSFunction
    fun addMatch(text: String, obj: Any?) {
        val match = TextMatch(text, obj)
        matches.add(match)
    }

    @JSFunction
    fun addMatchRe(pattern: String, obj: Any?) {
        val match = RegexMatch(Regex(pattern), obj)
        matches.add(match)
    }

    @JSFunction
    fun wait(): Any? {
        val context = Context.getCurrentContext()
        val contextFactory = context.factory as InterruptableContextFactory
        val instance = contextFactory.getInstance(context)
        val client = instance.client!!
        var result: Any? = null
        instance.checkStatus()
        try {
            runBlocking(instance.scope.coroutineContext) {
                client.eventFlow.first { event ->
                    if (instance.status == ScriptStatus.Running && event is ClientTextEvent) {
                        val match = matches.firstOrNull { it.matches(event.text) }
                        if (match != null) {
                            result = match.obj
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
            }
        } catch (_: InterruptedException) { }
        return result
    }
}

sealed class JsMatch(val obj: Any?) {
    abstract fun matches(line: String): Boolean
}

class TextMatch(val text: String, obj: Any?) : JsMatch(obj) {
    override fun matches(line: String): Boolean {
        return line.contains(text, ignoreCase = true)
    }
}

class RegexMatch(val regex: Regex, obj: Any?) : JsMatch(obj) {
    override fun matches(line: String): Boolean {
        return regex.find(line) != null
    }
}