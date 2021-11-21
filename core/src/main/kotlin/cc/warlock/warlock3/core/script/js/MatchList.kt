package cc.warlock.warlock3.core.script.js

import cc.warlock.warlock3.core.client.ClientTextEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.annotations.JSFunction

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
        val jsClient = parentScope.get("client", parentScope) as JavascriptClient?
        val client = jsClient?.client ?: return null
        var result: Any? = null
        runBlocking(jsClient.context) {
            client.eventFlow.first { event ->
                if (event is ClientTextEvent) {
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