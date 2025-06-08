package warlockfe.warlock3.scripting

import warlockfe.warlock3.core.prefs.ScriptDirRepository
import warlockfe.warlock3.core.script.ScriptLaunchResult
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

    private var nextId = 0L

    override suspend fun getScript(name: String, characterId: String, scriptManager: ScriptManager): ScriptLaunchResult {
        for (engine in engines) {
            for (extension in engine.extensions) {
                for (scriptDir in scriptDirRepository.getMappedScriptDirs(characterId)) {
                    val files = File(scriptDir).listFiles { file ->
                        file.extension.equals(extension, ignoreCase = true) && file.nameWithoutExtension.equals(name, ignoreCase = true)
                    }
                    if (files.size == 1) {
                        return ScriptLaunchResult.Success(engine.createInstance(nextId++, name, files.first(), scriptManager))
                    } else if (files.size > 1) {
                        return ScriptLaunchResult.Failure("Found multiple files that could be launched by that command")
                    }
                }
            }
        }
        return ScriptLaunchResult.Failure("Could not find a script with that name")
    }

    override suspend fun getScript(file: File, scriptManager: ScriptManager): ScriptLaunchResult {
        return if (file.exists()) {
            val engine = getEngineForExtension(file.extension) ?: return ScriptLaunchResult.Failure("Unsupported file extension - ${file.extension}")
            // client.print(StyledString("That extension is not supported"))
            ScriptLaunchResult.Success(engine.createInstance(nextId++, file.name, file, scriptManager))
        } else {
            ScriptLaunchResult.Failure("Could not find a script with that name")
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
