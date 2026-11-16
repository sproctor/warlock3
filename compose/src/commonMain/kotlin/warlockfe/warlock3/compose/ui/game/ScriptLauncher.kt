package warlockfe.warlock3.compose.ui.game

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import warlockfe.warlock3.core.client.ScriptableClient
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.prefs.models.Action
import warlockfe.warlock3.core.script.MudScriptLoad
import warlockfe.warlock3.core.script.MudScriptStore
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockStyle

/** The name the MUD's script runs under, so a new version replaces the last and `/kill` can find it. */
const val MUD_SCRIPT_NAME = "mud-script"

/**
 * Starts scripts against one game: from a file, from an action button, and the one a MUD sends.
 * Every script sends its commands through [commands]; one typed after the script prefix is started
 * by [CommandHandler] itself. [ScriptControl] acts on them once they are running.
 */
internal class ScriptLauncher(
    private val client: WarlockClient,
    private val scriptManager: ScriptManager,
    private val mudScriptStore: MudScriptStore,
    private val commands: CommandHandler,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {
    init {
        runTheMudsScript()
    }

    fun runFile(file: Path) {
        scope.launch(ioDispatcher) {
            scriptManager.startScript(client, file, commands::handle)
        }
    }

    /** Run a leaf action button's inline WSL script. No-op for a group (which has no script). */
    fun runAction(action: Action) {
        val script = action.script ?: return
        scope.launch(ioDispatcher) {
            scriptManager.startScript(client, action.name, script, commands::handle)
        }
    }

    /**
     * Runs the script a MUD offers over GMCP, when the connection is one that can be offered one
     * (see [ScriptableClient]) and the user has not declined. The offer is a state, not an event,
     * so one made while the connection was still being set up is not missed; a new version
     * offered mid-session replaces the running script, which goes by a fixed name for that.
     */
    private fun runTheMudsScript() {
        val scriptable = client as? ScriptableClient ?: return
        scope.launch(ioDispatcher) {
            scriptable.mudScript.filterNotNull().collect { offer ->
                val characterId = client.characterId.value ?: return@collect
                when (val load = mudScriptStore.load(characterId, offer)) {
                    is MudScriptLoad.Loaded -> {
                        client.print(StyledString("The MUD sent a script (version ${offer.version}).", WarlockStyle.Echo))
                        scriptManager.startScript(client, MUD_SCRIPT_NAME, load.script, commands::handle, extension = "lua")
                    }

                    is MudScriptLoad.Failed -> {
                        client.print(StyledString(load.message, WarlockStyle.Error))
                    }

                    is MudScriptLoad.Skipped -> {
                        client.debug(load.reason)
                    }
                }
            }
        }
    }
}
