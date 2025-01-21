package warlockfe.warlock3.scripting

import kotlinx.coroutines.CoroutineScope
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.script.ScriptManagerFactory
import warlockfe.warlock3.core.script.WarlockScriptEngineRepository

class ScriptManagerFactoryImpl(
    private val scriptEngineRepository: WarlockScriptEngineRepository,
    private val externalScope: CoroutineScope,
) : ScriptManagerFactory {
    override fun create(): ScriptManager {
        return ScriptManagerImpl(scriptEngineRepository, externalScope)
    }
}
