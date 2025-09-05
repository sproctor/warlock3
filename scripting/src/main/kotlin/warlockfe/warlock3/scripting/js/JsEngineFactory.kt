package warlockfe.warlock3.scripting.js

import warlockfe.warlock3.core.prefs.repositories.VariableRepository

class JsEngineFactory(
    private val variableRepository: VariableRepository,
) {
    fun create(): JsEngine {
        return JsEngine(variableRepository)
    }
}