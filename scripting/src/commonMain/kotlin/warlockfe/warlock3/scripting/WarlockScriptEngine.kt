package warlockfe.warlock3.scripting

import kotlinx.io.files.Path
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.core.script.ScriptManager

interface WarlockScriptEngine {
    val extensions: List<String>

    fun createInstance(
        id: Long,
        name: String,
        file: Path,
        scriptManager: ScriptManager,
    ): ScriptInstance

    /** Build an instance that runs an in-memory [content] string (an action button's script, or a MUD's). */
    fun createStringInstance(
        id: Long,
        name: String,
        content: String,
        scriptManager: ScriptManager,
    ): ScriptInstance
}
