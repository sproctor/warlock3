package warlockfe.warlock3.scripting.lua

import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import warlockfe.warlock3.core.client.SendCommandType
import warlockfe.warlock3.core.script.ScriptManager

/**
 * What a Lua script reaches beyond its connection: the script manager it starts and stops other
 * scripts through, the command handler they are started with, the script folders `require` loads
 * libraries from, and the folder `storage` keeps its data in (none: `storage` is unavailable).
 */
internal class LuaHost(
    val scriptManager: ScriptManager,
    val commandHandler: suspend (String) -> SendCommandType,
    val scriptDirs: suspend (characterId: String) -> List<Path>,
    val storageDir: Path?,
    val fileSystem: FileSystem,
)
