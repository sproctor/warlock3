package warlockfe.warlock3.core.script

import java.io.File

interface WarlockScriptEngine {

    val extensions: List<String>

    fun createInstance(name: String, file: File): ScriptInstance
}