package warlockfe.warlock3.scripting.js

import warlockfe.warlock3.core.prefs.VariableRepository

class JsEngineFactory(
    private val variableRepository: VariableRepository,
) {
    fun create(): JsEngine {
        return JsEngine(variableRepository)
    }
}