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

    override suspend fun getScript(
        name: String,
        characterId: String,
        scriptManager: ScriptManager
    ): ScriptLaunchResult {
        val matchedFiles = mutableListOf<Pair<WarlockScriptEngine, File>>()
        for (engine in engines) {
            for (scriptDir in scriptDirRepository.getMappedScriptDirs(characterId)) {
                File(scriptDir)
                    .listFiles { file ->
                        engine.extensions.any { file.extension.equals(it, ignoreCase = true) } &&
                                file.nameWithoutExtension.equals(name, ignoreCase = true)
                    }
                    ?.map { engine to it }
                    ?.let { matchedFiles.addAll(it) }
            }
        }
        return if (matchedFiles.size == 1) {
            val entry = matchedFiles.first()
            ScriptLaunchResult.Success(
                entry.first.createInstance(nextId++, name, entry.second, scriptManager)
            )
        } else if (matchedFiles.size > 1) {
            ScriptLaunchResult.Failure("Found multiple files that could be launched by that command")
        } else {
            ScriptLaunchResult.Failure("Could not find a script with that name")
        }
    }

    override suspend fun getScript(file: File, scriptManager: ScriptManager): ScriptLaunchResult {
        return if (file.exists()) {
            val engine = getEngineForExtension(file.extension)
                ?: return ScriptLaunchResult.Failure("Unsupported file extension - ${file.extension}")
            ScriptLaunchResult.Success(engine.createInstance(nextId++, file.name, file, scriptManager))
        } else {
            ScriptLaunchResult.Failure("Could not find a script with that name")
        }
    }

    private fun getEngineForExtension(extension: String): WarlockScriptEngine? {
        for (engine in engines) {
            if (engine.extensions.any { extension.equals(it, ignoreCase = true) }) {
                return engine
            }
        }
        return null
    }

    override fun supportsExtension(extension: String): Boolean {
        return getEngineForExtension(extension) != null
    }
}
