package warlockfe.warlock3.scripting

import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.core.script.ScriptManager
import java.io.File

interface WarlockScriptEngine {

    val extensions: List<String>

    fun createInstance(id: Long, name: String, file: File, scriptManager: ScriptManager): ScriptInstance
}