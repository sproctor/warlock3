package warlockfe.warlock3.scripting

import kotlinx.io.files.Path
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.core.script.ScriptManager

interface WarlockScriptEngine {

    val extensions: List<String>

    fun createInstance(id: Long, name: String, file: Path, scriptManager: ScriptManager): ScriptInstance
}