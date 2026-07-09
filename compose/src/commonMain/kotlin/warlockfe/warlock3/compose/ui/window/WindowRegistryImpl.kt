package warlockfe.warlock3.compose.ui.window

import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import warlockfe.warlock3.compose.model.LiteralHighlight
import warlockfe.warlock3.compose.model.RegexHighlight
import warlockfe.warlock3.compose.model.ViewHighlight
import warlockfe.warlock3.compose.util.HighlightIndex
import warlockfe.warlock3.core.prefs.repositories.AlterationRepository
import warlockfe.warlock3.core.prefs.repositories.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.HighlightRepository
import warlockfe.warlock3.core.prefs.repositories.NameRepository
import warlockfe.warlock3.core.prefs.repositories.PresetRepository
import warlockfe.warlock3.core.prefs.repositories.WindowSettingsRepository
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.util.SoundPlayer
import warlockfe.warlock3.core.window.DialogState
import warlockfe.warlock3.core.window.TextStream
import warlockfe.warlock3.core.window.WindowRegistry
import warlockfe.warlock3.wrayth.util.CompiledAlteration
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalAtomicApi::class)
class WindowRegistryImpl(
    private val soundPlayer: SoundPlayer,
    settingRepository: ClientSettingRepository,
    highlightRepository: HighlightRepository,
    nameRepository: NameRepository,
    alterationRepository: AlterationRepository,
    presetRepository: PresetRepository,
    private val characterSettingsRepository: CharacterSettingsRepository,
    private val windowSettingsRepository: WindowSettingsRepository,
    skinPresets: StateFlow<Map<String, StyleDefinition>>,
) : WindowRegistry {
    private val streams = AtomicReference(persistentMapOf<String, ComposeTextStream>())

    private val dialogs = AtomicReference(persistentMapOf<String, ComposeDialogState>())

    private val scope = CoroutineScope(SupervisorJob())

    private val workQueue = StreamWorkQueue(scope = scope)

    private val maxLines =
        settingRepository
            .observeMaxScrollLines()
            .onEach {
                streams.load().values.forEach { stream ->
                    stream.setMaxLines(it)
                }
            }.stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = ClientSettingRepository.DEFAULT_MAX_SCROLL_LINES,
            )

    private val markLinks =
        settingRepository
            .observeMarkLinks()
            .onEach {
                streams.load().values.forEach { stream ->
                    stream.setMarkLinks(it)
                }
            }.stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = true,
            )

    private val showImages =
        settingRepository
            .observeShowImages()
            .onEach {
                streams.load().values.forEach { stream ->
                    stream.setShowImages(it)
                }
            }.stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = true,
            )

    private val suppressPrompts =
        settingRepository
            .observeSuppressPrompts()
            .onEach {
                streams.load().values.forEach { stream ->
                    stream.setSuppressPrompts(it)
                }
            }.stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = false,
            )

    private val characterId = MutableStateFlow("global")

    private val names: StateFlow<List<ViewHighlight>> =
        characterId
            .flatMapLatest { characterId ->
                nameRepository.observeForCharacter(characterId).map { names ->
                    names.mapNotNull { name ->
                        if (name.text.isBlank()) return@mapNotNull null
                        LiteralHighlight(
                            literal = name.text,
                            matchPartialWord = false,
                            ignoreCase = false,
                            style =
                                StyleDefinition(
                                    textColor = name.textColor,
                                    backgroundColor = name.backgroundColor,
                                ),
                            sound = name.sound,
                        )
                    }
                }
            }.stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    private val highlights: StateFlow<HighlightIndex> =
        characterId
            .flatMapLatest { characterId ->
                highlightRepository.observeForCharacter(characterId).map { highlights ->
                    highlights.mapNotNull { highlight ->
                        if (highlight.isRegex) {
                            try {
                                RegexHighlight(
                                    regex =
                                        Regex(
                                            pattern = highlight.pattern,
                                            options = if (highlight.ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet(),
                                        ),
                                    styles = highlight.styles,
                                    sound = highlight.sound,
                                )
                            } catch (_: Exception) {
                                // client.debug("Error while parsing highlight (${e.message}): $highlight")
                                null
                            }
                        } else if (highlight.pattern.isBlank()) {
                            null
                        } else {
                            LiteralHighlight(
                                literal = highlight.pattern,
                                matchPartialWord = highlight.matchPartialWord,
                                ignoreCase = highlight.ignoreCase,
                                style = highlight.styles[0],
                                sound = highlight.sound,
                            )
                        }
                    }
                }
            }.let { generalHighlights ->
                combine(generalHighlights, names) { general, nameHighlights ->
                    general + nameHighlights
                }
            }
            // Build the matching index once per highlight-list change, here in the flow, rather than per
            // line on the render path. The resulting index is shared by every window stream, so the
            // O(highlights) build happens once per change in total instead of once per stream.
            .map { HighlightIndex(it) }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = HighlightIndex(emptyList()),
            )

    private val alterations: StateFlow<List<CompiledAlteration>> =
        characterId
            .flatMapLatest { characterId ->
                alterationRepository.observeForCharacter(characterId).map { list ->
                    list.mapNotNull {
                        try {
                            CompiledAlteration(it)
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }.stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    // Skin presets are the defaults, overridden by the global (all-characters) presets, overridden by
    // the character's own presets (skin -> global -> character).
    override val presets: StateFlow<Map<String, StyleDefinition>> =
        combine(
            skinPresets,
            characterId.flatMapLatest { presetRepository.observeForCharacter(it) },
        ) { skinDefaults, savedPresets -> skinDefaults + savedPresets }
            .stateIn(scope = this.scope, started = SharingStarted.Eagerly, initialValue = emptyMap())

    // The effective monospace font for a given window: its per-window override if set, otherwise the
    // character's default monospace font. Rebuilt per character; a change re-renders that window's buffer.
    private fun monoFontFor(name: String): StateFlow<FontConfig?> =
        characterId
            .flatMapLatest { cid ->
                combine(
                    characterSettingsRepository.observeMonoFont(cid),
                    windowSettingsRepository
                        .observeWindowSettings(cid)
                        .map { windows -> windows.firstOrNull { it.name == name }?.monoFont },
                ) { characterMono, windowMono -> windowMono ?: characterMono }
            }.distinctUntilChanged()
            .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = null)

    override fun getOrCreateStream(name: String): TextStream {
        streams.load()[name]?.let { return it }
        val candidate =
            ComposeTextStream(
                id = name,
                maxLines = maxLines.value,
                highlights = highlights,
                names = names,
                alterations = alterations,
                presets = presets,
                monoFont = monoFontFor(name),
                soundPlayer = soundPlayer,
                markLinks = markLinks.value,
                showImages = showImages.value,
                showTimestamps = false,
                suppressPrompts = suppressPrompts.value,
                workQueue = workQueue,
                scope = scope,
            )
        while (true) {
            val current = streams.load()
            current[name]?.let { return it }
            if (streams.compareAndSet(current, current.putting(name, candidate))) {
                return candidate
            }
        }
    }

    override fun getStreams(): Collection<TextStream> = streams.load().values

    // A server component update affects every window that displays that component. Batch it into one
    // work-queue op that touches each stream, instead of each stream enqueuing its own op: with many
    // windows open the per-stream broadcast multiplied queue traffic by the window count for every
    // component change. Streams that don't display the component fall through cheaply.
    override suspend fun updateComponent(
        name: String,
        value: StyledString,
    ) {
        workQueue.submit("component") {
            streams.load().values.forEach { it.updateComponentSync(name, value) }
        }
    }

    override fun getOrCreateDialog(name: String): DialogState {
        dialogs.load()[name]?.let { return it }
        val candidate = ComposeDialogState(id = name)
        while (true) {
            val current = dialogs.load()
            current[name]?.let { return it }
            if (dialogs.compareAndSet(current, current.putting(name, candidate))) {
                return candidate
            }
        }
    }

    override fun setCharacterId(characterId: String) {
        this.characterId.value = characterId
        workQueue.tag = characterId
    }

    override fun close() {
        scope.cancel()
    }
}

class WindowRegistryFactory(
    private val soundPlayer: SoundPlayer,
    private val settingRepository: ClientSettingRepository,
    private val highlightRepository: HighlightRepository,
    private val nameRepository: NameRepository,
    private val alterationRepository: AlterationRepository,
    private val presetRepository: PresetRepository,
    private val characterSettingsRepository: CharacterSettingsRepository,
    private val windowSettingsRepository: WindowSettingsRepository,
    private val skinPresets: StateFlow<Map<String, StyleDefinition>>,
) {
    fun create(): WindowRegistry =
        WindowRegistryImpl(
            soundPlayer = soundPlayer,
            settingRepository = settingRepository,
            highlightRepository = highlightRepository,
            nameRepository = nameRepository,
            alterationRepository = alterationRepository,
            presetRepository = presetRepository,
            characterSettingsRepository = characterSettingsRepository,
            windowSettingsRepository = windowSettingsRepository,
            skinPresets = skinPresets,
        )
}
