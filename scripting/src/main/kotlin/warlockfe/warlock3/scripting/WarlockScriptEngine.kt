package warlockfe.warlock3.scripting

import warlockfe.warlock3.core.script.ScriptInstance
import java.io.File

interface WarlockScriptEngine {

    val extensions: List<String>

    fun createInstance(name: String, id: Long, file: File): ScriptInstance
}