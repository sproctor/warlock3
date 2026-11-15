package warlockfe.warlock3.compose.ui.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import warlockfe.warlock3.compose.util.LatestValueWriter
import warlockfe.warlock3.core.prefs.repositories.CharacterSettingsRepository
import warlockfe.warlock3.core.window.WindowInfo

// The phone tab strip's own ordered window list, one blob per character. Deliberately separate from
// the WindowSettings "open" flag that feeds the docking layout: the phone picks its own tabs, and
// adding one must not rearrange a dock the user is not looking at.
const val PHONE_TAB_ORDER_KEY = "phoneTabOrder"

/** The phone tab strip: which windows have a tab, in what order, saved per character. */
internal class PhoneTabs(
    private val characterId: StateFlow<String?>,
    private val characterSettingsRepository: CharacterSettingsRepository,
    tabbableWindows: Flow<List<WindowInfo>>,
    private val scope: CoroutineScope,
) {
    private val savedOrder = MutableStateFlow<PhoneTabOrder>(PhoneTabOrder.Loading)

    /**
     * The strip's window names, in order. Always holds the main window.
     *
     * Eagerly started because the mutators below read its value: an add or a drag in the first
     * frames of a session has to see the real list, not the initial one.
     */
    val tabs: StateFlow<List<String>> =
        combine(savedOrder, tabbableWindows, ::reconcilePhoneTabs)
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = listOf(MAIN_WINDOW_NAME),
            )

    // Reordering fires on every step of a drag, so two writes that finished out of order would leave
    // the older order stored with nothing to correct it.
    private val writer = LatestValueWriter(::write)

    init {
        characterId
            .observePerCharacter { characterSettingsRepository.observe(it, PHONE_TAB_ORDER_KEY) }
            .onEach { raw -> savedOrder.value = decodePhoneTabOrder(raw) }
            .launchIn(scope)
    }

    /** Puts [name] in the strip. Does nothing if it is already there. */
    fun add(name: String) = edit { if (name in it) it else it + name }

    /** Takes [name] out of the strip. The main window cannot be removed. */
    fun remove(name: String) {
        if (name == MAIN_WINDOW_NAME) return
        edit { it - name }
    }

    /** Moves a tab, with [from] and [to] as positions in the strip the user can see. */
    fun move(
        from: Int,
        to: Int,
    ) {
        val shown = tabs.value
        if (from == to || from !in shown.indices || to !in shown.indices) return
        edit { saved ->
            moveInSavedOrder(saved, moved = shown[from], anchor = shown[to], forward = to > from)
        }
    }

    /** Writes an order a debounced save has not gotten to yet. */
    suspend fun flush() {
        writer.flush()
    }

    /**
     * The one way the tab list changes, and where an [PhoneTabOrder.Auto] list becomes the user's
     * own: the first edit freezes what is on screen, so nothing they could see silently drops out.
     *
     * [transform] is handed the *saved* order, not the displayed one. A window the game has not
     * announced this session is missing from the strip but must survive the edit.
     */
    private fun edit(transform: (List<String>) -> List<String>) {
        val order = savedOrder.value
        // Settings have not answered yet, so the strip is showing a placeholder. Editing against it
        // would write [main] over the order this character already has saved.
        if (order is PhoneTabOrder.Loading) return
        val current = (order as? PhoneTabOrder.Explicit)?.names ?: tabs.value
        val next = transform(current)
        if (next == current) return
        savedOrder.value = PhoneTabOrder.Explicit(next)
        scope.launch { writer.write(encodePhoneTabOrder(next)) }
    }

    private suspend fun write(order: String) {
        characterId.value?.let { characterId ->
            characterSettingsRepository.save(characterId, PHONE_TAB_ORDER_KEY, order)
        }
    }
}
