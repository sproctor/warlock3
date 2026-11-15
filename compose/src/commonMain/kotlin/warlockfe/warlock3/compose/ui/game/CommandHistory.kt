package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.CommandHistoryRepository

/**
 * The commands sent from [entry], which the arrow keys step through and the {HistorySearch} macro
 * searches, saved per character.
 */
internal class CommandHistory(
    private val entry: TextFieldState,
    private val characterId: StateFlow<String?>,
    private val commandHistoryRepository: CommandHistoryRepository,
    clientSettingRepository: ClientSettingRepository,
    private val scope: CoroutineScope,
) {
    // sendHistory[0] is the in-progress entry buffer; indices 1..n are the stored commands,
    // newest first, so the list holds at most historySize + 1 elements.
    private val sendHistory = mutableListOf("")
    private var historyPosition = 0

    // Commands shorter than this are not saved to history; history is capped at historySize entries.
    // Both are seeded from client settings; the defaults apply until the flows emit.
    private var minHistoryLen = ClientSettingRepository.DEFAULT_MIN_COMMAND_LENGTH
    private var historySize = ClientSettingRepository.DEFAULT_HISTORY_SIZE

    // Reverse history search (readline-style ctrl+r). Non-null while the entry is in "searching"
    // mode; it holds the live query (which mirrors the entry text the user types) and the history
    // command currently matched. searchIndex selects which match, counting back from the most
    // recent (0).
    private val _search = MutableStateFlow<HistorySearchState?>(null)
    val search: StateFlow<HistorySearchState?> = _search.asStateFlow()
    private var searchIndex = 0

    val searching: Boolean
        get() = _search.value != null

    init {
        clientSettingRepository
            .observeMinCommandLength()
            .onEach { minHistoryLen = it }
            .launchIn(scope)
        clientSettingRepository
            .observeHistorySize()
            .onEach {
                historySize = it
                trim()
            }.launchIn(scope)
        loadOnConnect()
        trackSearchQuery()
    }

    // Load each character's saved command history when it connects.
    private fun loadOnConnect() {
        characterId
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { characterId ->
                val saved = commandHistoryRepository.load(characterId)
                // The file stores commands oldest-first, so reverse them on load.
                sendHistory.clear()
                sendHistory.add("")
                saved.asReversed().forEach { sendHistory.add(it) }
                trim()
                historyPosition = 0
            }.launchIn(scope)
    }

    private fun trackSearchQuery() {
        scope.launch {
            // While searching, the entry text is the live query; whenever it changes, re-run the
            // search from the most recent match. Ignored when not in search mode.
            snapshotFlow { entry.text.toString() }
                .collect {
                    if (_search.value != null) {
                        updateSearch(resetIndex = true)
                    }
                }
        }
    }

    /** Records [line] as the newest command and returns to the in-progress entry. */
    fun record(line: String) {
        if (line.length >= minHistoryLen && sendHistory.getOrNull(1) != line) {
            sendHistory[0] = line
            sendHistory.add(0, "")
            trim()
            persist()
        }
        historyPosition = 0
    }

    /** The command [index] steps back from the in-progress entry, or null when there is none. */
    fun commandAt(index: Int): String? = sendHistory.getOrNull(index)

    // Persist the current command history (commands only, oldest first) for the connected character.
    private fun persist() {
        val characterId = characterId.value ?: return
        val commands = sendHistory.drop(1).filter { it.isNotEmpty() }.asReversed()
        scope.launch {
            commandHistoryRepository.save(characterId, commands)
        }
    }

    private fun trim() {
        while (sendHistory.size > historySize + 1) {
            sendHistory.removeAt(sendHistory.size - 1)
        }
    }

    fun previous() {
        if (historyPosition < sendHistory.size - 1) {
            sendHistory[historyPosition] = entry.text.toString()
            historyPosition++
            entry.setTextAndPlaceCursorAtEnd(sendHistory[historyPosition])
        }
    }

    fun next() {
        if (historyPosition > 0) {
            sendHistory[historyPosition] = entry.text.toString()
            historyPosition--
            entry.setTextAndPlaceCursorAtEnd(sendHistory[historyPosition])
        }
    }

    /** Enters search mode with an empty query, or steps back to the next older match when already in it. */
    fun searchBack() {
        if (_search.value == null) {
            // The entry text becomes the query.
            searchIndex = 0
            historyPosition = 0
            entry.clearText()
            updateSearch(resetIndex = true)
        } else {
            searchIndex++
            updateSearch(resetIndex = false)
        }
    }

    /** Leaves search mode, keeping the matched command in the entry for editing or sending. */
    fun exitSearch() {
        val current = _search.value ?: return
        _search.value = null
        searchIndex = 0
        entry.setTextAndPlaceCursorAtEnd(current.match ?: "")
    }

    /**
     * Leaves search mode with an empty entry, recording the matched command as sent. Returns that
     * command, or null when nothing matched. Call only while [searching].
     */
    fun acceptSearch(): String? {
        val match = _search.value?.match
        _search.value = null
        searchIndex = 0
        entry.clearText()
        if (match != null) record(match)
        return match
    }

    // Recompute the current match for the active history search from the live query (the entry
    // text). A blank or otherwise non-matching query keeps the previously matched entry selected
    // rather than clearing it.
    private fun updateSearch(resetIndex: Boolean) {
        val query = entry.text.toString()
        val matches =
            if (query.isBlank()) {
                emptyList()
            } else {
                sendHistory.drop(1).filter { it.isNotEmpty() && it.contains(query, ignoreCase = true) }
            }
        val match =
            if (matches.isEmpty()) {
                _search.value?.match
            } else {
                if (resetIndex) {
                    searchIndex = 0
                }
                searchIndex = searchIndex.coerceIn(0, matches.size - 1)
                matches[searchIndex]
            }
        _search.value = HistorySearchState(query = query, match = match)
    }
}

/**
 * UI state for readline-style reverse history search. [query] is what the user has typed so far and
 * [match] is the history command currently matched (or null if nothing matches). While this is
 * non-null the entry prompt shows `searching "<query>": <match>`.
 */
data class HistorySearchState(
    val query: String,
    val match: String?,
)
