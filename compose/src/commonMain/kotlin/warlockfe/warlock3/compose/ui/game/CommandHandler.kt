package warlockfe.warlock3.compose.ui.game

import kotlinx.coroutines.flow.StateFlow
import warlockfe.warlock3.core.client.SendCommandType
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.script.ScriptManager
import warlockfe.warlock3.core.text.Alias
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.core.util.splitFirstWord

const val CLIENT_COMMAND_PREFIX = '/'

/**
 * Where a command line goes, after the character's aliases are applied: one starting with the
 * script prefix starts a script, one starting with [CLIENT_COMMAND_PREFIX] is the client's own
 * (`/kill`, `/list`, ...), and anything else is sent to the game. Scripts send their commands
 * through here too.
 */
internal class CommandHandler(
    private val client: WarlockClient,
    private val scriptManager: ScriptManager,
    private val scripts: ScriptControl,
    private val aliases: StateFlow<List<Alias>>,
    private val scriptCommandPrefix: StateFlow<String>,
) {
    private fun applyAliases(line: String): String = aliases.value.fold(line) { acc, alias -> alias.replace(acc) }

    /** Runs [line], returning what kind of command it turned out to be. */
    suspend fun handle(line: String): SendCommandType {
        val aliasedLine = applyAliases(line)
        val prefix = scriptCommandPrefix.value
        return if (aliasedLine.startsWith(prefix)) {
            client.print(StyledString(aliasedLine, WarlockStyle.Command))
            scriptManager.startScript(client, aliasedLine.drop(prefix.length), ::handle)
            SendCommandType.SCRIPT
        } else if (aliasedLine.startsWith(CLIENT_COMMAND_PREFIX)) {
            client.print(StyledString(aliasedLine, WarlockStyle.Command))
            handleClientCommand(aliasedLine.drop(1))
            SendCommandType.ACTION
        } else {
            client.sendCommand(aliasedLine)
            SendCommandType.COMMAND
        }
    }

    private suspend fun handleClientCommand(clientCommand: String) {
        val (command, args) = clientCommand.splitFirstWord()
        when (command) {
            "kill" -> {
                scripts.stop(scriptNames(args))
            }

            "pause" -> {
                scripts.pause(scriptNames(args))
            }

            "resume" -> {
                scripts.resume(scriptNames(args))
            }

            "list" -> {
                scripts.printRunning()
            }

            "disconnect", "dc" -> {
                client.disconnect()
            }

            "send" -> {
                client.sendCommandDirect(args ?: "")
            }

            else -> {
                client.print(StyledString("Invalid command.", WarlockStyle.Error))
            }
        }
    }

    // The names or ids after a script command, separated by spaces. Never an empty one: a script is
    // found by a prefix of its name, which an empty name is of every script.
    private fun scriptNames(args: String?): List<String> = args?.split(' ')?.filter { it.isNotEmpty() }.orEmpty()
}
