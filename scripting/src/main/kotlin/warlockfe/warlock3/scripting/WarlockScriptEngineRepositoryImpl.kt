package warlockfe.warlock3.scripting

import warlockfe.warlock3.core.prefs.ScriptDirRepository
import warlockfe.warlock3.core.script.ScriptInstance
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.script.WarlockScriptEngineRepository
import warlockfe.warlock3.scripting.js.JsEngineFactory
import warlockfe.warlock3.scripting.wsl.WslEngineFactory
import java.io.File

class WarlockScriptEngineRepositoryImpl(
    wslEngineFactory: WslEngineFactory,
    jsEngineFactory: JsEngineFactory,
    private val scriptDirRepository: ScriptDirRepository,
) : WarlockScriptEngineRepository {
    private val engines = listOf(
        wslEngineFactory.create(),
        jsEngineFactory.create(),
    )

    override suspend fun getScript(name: String, characterId: String, scriptManager: ScriptManager): ScriptInstance? {
        for (engine in engines) {
            for (extension in engine.extensions) {
                for (scriptDir in scriptDirRepository.getMappedScriptDirs(characterId)) {
                    val file = File("$scriptDir/$name.$extension")
                    if (file.exists()) {
                        return engine.createInstance(name, file, scriptManager)
                    }
                }
            }
        }
        return null
    }

    override suspend fun getScript(file: File, scriptManager: ScriptManager): ScriptInstance? {
        return if (file.exists()) {
            val engine = getEngineForExtension(file.extension) ?: return null // TODO: find a way to print error messages here
            // client.print(StyledString("That extension is not supported"))
            engine.createInstance(file.name, file, scriptManager)
        } else {
            null
        }
    }

    private fun getEngineForExtension(extension: String): WarlockScriptEngine? {
        for (engine in engines) {
            for (validExtension in engine.extensions) {
                if (extension == validExtension) {
                    return engine
                }
            }
        }
        return null
    }

    override fun supportsExtension(extension: String): Boolean {
        return getEngineForExtension(extension) != null
    }
}
