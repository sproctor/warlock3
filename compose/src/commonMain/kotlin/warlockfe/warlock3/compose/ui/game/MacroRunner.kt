package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.util.insertReplacingSelection
import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.macro.MacroCommands
import warlockfe.warlock3.core.macro.MacroHandler
import warlockfe.warlock3.core.macro.MacroKeyCombo
import warlockfe.warlock3.core.macro.MacroToken
import warlockfe.warlock3.core.macro.parseMacro
import warlockfe.warlock3.core.prefs.repositories.MacroRepository
import warlockfe.warlock3.core.prefs.repositories.VariableRepository
import warlockfe.warlock3.core.text.StyledString
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the macro bound to a key press: its text goes into [entry], its `{Command}`s go to
 * [handler], and its entities (`\x`, `\r`, `\p`, `\?`) do their own thing. The connected
 * character's macros apply, or the global ones before a character is known.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class MacroRunner(
    private val client: WarlockClient,
    macroRepository: MacroRepository,
    private val variableRepository: VariableRepository,
    private val entry: TextFieldState,
    private val handler: MacroHandler,
    private val history: CommandHistory,
    private val commands: CommandHandler,
    private val submit: () -> Unit,
    private val scope: CoroutineScope,
) {
    private val macros =
        client.characterId
            .flatMapLatest { characterId ->
                if (characterId != null) {
                    macroRepository.observeCharacterMacros(characterId)
                } else {
                    macroRepository.observeGlobalMacros()
                }.map { macroCommands ->
                    macroCommands.associate { it.keyCombo to it.action }
                }
            }.stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = emptyMap(),
            )

    private val _error = MutableStateFlow<String?>(null)

    /** A macro command that could not be run, until the UI reports it shown with [errorShown]. */
    val error: StateFlow<String?> = _error.asStateFlow()

    // The entry text a `\x` entity cleared, which a `\?` entity puts back.
    private var storedText: String = ""

    fun errorShown() {
        _error.value = null
    }

    /** Runs the macro bound to [event]'s key combination, returning whether the key was used. */
    fun handleKeyPress(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || event.key == Key.Unknown) {
            return false
        }

        val macroString = macros.value[event.toKeyCombo()]

        if (macroString != null) {
            val tokens = parseMacro(macroString)

            if (tokens == null) {
                scope.launch {
                    client.print(StyledString("Invalid macro: $macroString"))
                }
                return false
            }

            execute(tokens)

            return true
        }

        if (event.key.keyCode == Key.Enter.keyCode) {
            submit()
            return true
        }

        return false
    }

    private fun execute(tokens: List<MacroToken>) {
        scope.launch {
            var moveCursor: Int? = null
            tokens.forEach { token ->
                when (token) {
                    is MacroToken.Entity -> {
                        handleEntity(
                            entity = token.char,
                            onEntryCleared = {
                                // TODO: report this as an error
                                moveCursor = null
                            },
                        )
                    }

                    MacroToken.At -> {
                        moveCursor = entry.selection.min
                    }

                    is MacroToken.Text -> {
                        entry.insertReplacingSelection(token.text)
                    }

                    is MacroToken.Variable -> {
                        client.characterId.value?.lowercase()?.let { characterId ->
                            variableRepository.getVariable(characterId, token.name)?.let { value ->
                                entry.insertReplacingSelection(value)
                            }
                        }
                    }

                    is MacroToken.Command -> {
                        if (!MacroCommands.execute(token.name, handler)) {
                            _error.value = "Macro command not found: ${token.name}"
                        }
                    }
                }
            }
            moveCursor?.let { position ->
                entry.edit {
                    selection = TextRange(position)
                }
            }
        }
    }

    private suspend fun handleEntity(
        entity: Char,
        onEntryCleared: () -> Unit,
    ) {
        when (entity) {
            'x' -> {
                storedText = entry.text.toString()
                entry.clearText()
                onEntryCleared()
            }

            'r' -> {
                val line = entry.text.toString()
                entry.clearText()
                history.record(line)
                commands.handle(commands.applyAliases(line))
                onEntryCleared()
            }

            'p' -> {
                delay(1.seconds)
            }

            '?' -> {
                entry.edit {
                    append(storedText)
                }
            }
        }
    }
}

// TODO: convert this into a simpler representation
private fun KeyEvent.toKeyCombo(): MacroKeyCombo =
    MacroKeyCombo(
        keyCode = key.keyCode,
        ctrl = isCtrlPressed,
        alt = isAltPressed,
        shift = isShiftPressed,
        meta = isMetaPressed,
    )
