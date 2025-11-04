package warlockfe.warlock3.scripting

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import warlockfe.warlock3.core.prefs.repositories.ScriptDirRepository
import warlockfe.warlock3.core.script.ScriptLaunchResult
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.script.WarlockScriptEngineRepository
import warlockfe.warlock3.scripting.util.extension
import warlockfe.warlock3.scripting.util.nameWithoutExtension

class WarlockScriptEngineRepositoryImpl(
    private val engines: List<WarlockScriptEngine>,
    private val fileSystem: FileSystem,
    private val scriptDirRepository: ScriptDirRepository,
) : WarlockScriptEngineRepository {

    private var nextId = 0L

    override suspend fun getScript(
        name: String,
        characterId: String,
        scriptManager: ScriptManager
    ): ScriptLaunchResult {
        val matchedFiles = mutableListOf<Pair<WarlockScriptEngine, Path>>()
        for (engine in engines) {
            for (scriptDir in scriptDirRepository.getMappedScriptDirs(characterId)) {
                if (fileSystem.exists(scriptDir)) {
                    fileSystem.list(scriptDir)
                        .filter { file ->
                            engine.extensions.any { extension ->
                                file.extension.equals(extension, ignoreCase = true) &&
                                        (file.nameWithoutExtension.equals(name, ignoreCase = true)
                                                || file.name.equals(name, ignoreCase = true))
                            }
                        }
                        .forEach {
                            matchedFiles.add(engine to it)
                        }
                }
            }
        }
        return if (matchedFiles.isNotEmpty()) {
            // TODO: warn about multiple scripts
//            if (matchedFiles.size > 1) {
//                ScriptLaunchResult.Failure("Found multiple files that could be launched by that command")
//            }
            val entry = matchedFiles.first()
            ScriptLaunchResult.Success(
                entry.first.createInstance(nextId++, name, entry.second, scriptManager)
            )
        } else {
            ScriptLaunchResult.Failure("Could not find a script with that name")
        }
    }

    override suspend fun getScript(file: Path, scriptManager: ScriptManager): ScriptLaunchResult {
        return if (fileSystem.exists(file)) {
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
