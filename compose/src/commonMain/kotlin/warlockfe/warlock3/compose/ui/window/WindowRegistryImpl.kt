package warlockfe.warlock3.compose.ui.window

import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import warlockfe.warlock3.compose.model.ViewHighlight
import warlockfe.warlock3.core.prefs.repositories.AlterationRepository
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import warlockfe.warlock3.core.prefs.repositories.HighlightRepository
import warlockfe.warlock3.core.prefs.repositories.NameRepository
import warlockfe.warlock3.core.prefs.repositories.PresetRepository
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.util.SoundPlayer
import warlockfe.warlock3.core.window.DialogState
import warlockfe.warlock3.core.window.TextStream
import warlockfe.warlock3.core.window.WindowRegistry
import warlockfe.warlock3.wrayth.util.CompiledAlteration

@OptIn(ExperimentalCoroutinesApi::class)
class WindowRegistryImpl(
    private val ioDispatcher: CoroutineDispatcher,
    private val soundPlayer: SoundPlayer,
    externalScope: CoroutineScope,
    settingRepository: ClientSettingRepository,
    highlightRepository: HighlightRepository,
    nameRepository: NameRepository,
    alterationRepository: AlterationRepository,
    presetRepository: PresetRepository,
) : WindowRegistry {

    private var streams = persistentMapOf<String, ComposeTextStream>()

    private var dialogs = persistentMapOf<String, ComposeDialogState>()

    private val maxLines = settingRepository
        .observeMaxScrollLines()
        .onEach {
            streams.values.forEach { stream ->
                stream.setMaxLines(it)
            }
        }
        .stateIn(
            scope = externalScope,
            started = SharingStarted.Eagerly,
            initialValue = ClientSettingRepository.DEFAULT_MAX_SCROLL_LINES,
        )

    private val markLinks = settingRepository
        .observeMarkLinks()
        .onEach {
            streams.values.forEach { stream ->
                stream.setMarkLinks(it)
            }
        }
        .stateIn(
            scope = externalScope,
            started = SharingStarted.Eagerly,
            initialValue = true,
        )

    private val characterId = MutableStateFlow<String>("global")

    private val highlights: StateFlow<List<ViewHighlight>> = characterId.flatMapLatest { characterId ->
        combine(
            highlightRepository.observeForCharacter(characterId),
            nameRepository.observeForCharacter(characterId)
        ) { highlights, names ->
            val generalHighlights = highlights.mapNotNull { highlight ->
                val pattern = if (highlight.isRegex) {
                    highlight.pattern
                } else {
                    val subpattern = Regex.escape(highlight.pattern)
                    if (highlight.matchPartialWord) {
                        subpattern
                    } else {
                        "\\b$subpattern\\b"
                    }
                }
                try {
                    ViewHighlight(
                        regex = Regex(
                            pattern = pattern,
                            options = if (highlight.ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet(),
                        ),
                        styles = highlight.styles,
                        sound = highlight.sound,
                    )
                } catch (_: Exception) {
                    // client.debug("Error while parsing highlight (${e.message}): $highlight")
                    null
                }
            }
            val nameHighlights = names.mapNotNull { name ->
                val pattern = Regex.escape(name.text).let { "\\b$it\\b" }
                try {
                    ViewHighlight(
                        regex = Regex(pattern = pattern),
                        styles = mapOf(
                            0 to StyleDefinition(
                                textColor = name.textColor,
                                backgroundColor = name.backgroundColor,
                            )
                        ),
                        sound = name.sound,
                    )
                } catch (_: Exception) {
                    // client.debug("Error while parsing highlight (${e.message}): $name")
                    null
                }
            }
            generalHighlights + nameHighlights
        }
    }
        .stateIn(
            scope = externalScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    private val alterations: StateFlow<List<CompiledAlteration>> = characterId.flatMapLatest { characterId ->
        alterationRepository.observeForCharacter(characterId).map { list ->
            list.mapNotNull {
                try {
                    CompiledAlteration(it)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
        .stateIn(scope = externalScope, started = SharingStarted.Eagerly, initialValue = emptyList())

    override val presets: StateFlow<Map<String, StyleDefinition>> =
        characterId.flatMapLatest { characterId ->
            presetRepository.observePresetsForCharacter(characterId)
        }
            .stateIn(scope = externalScope, started = SharingStarted.Eagerly, initialValue = emptyMap())

    override fun getOrCreateStream(name: String): TextStream {
        return streams.getOrElse(name) {
            ComposeTextStream(
                id = name,
                maxLines = maxLines.value,
                highlights = highlights,
                alterations = alterations,
                presets = presets,
                ioDispatcher = ioDispatcher,
                soundPlayer = soundPlayer,
                markLinks = markLinks.value,
                showTimestamps = false,
            )
                .also { streams = streams.put(name, it) }
        }
    }

    override fun getStreams(): Collection<TextStream> {
        return streams.values
    }

    override fun getOrCreateDialog(name: String): DialogState {
        return dialogs.getOrElse(name) {
            ComposeDialogState(
                id = name,
            )
                .also { dialogs = dialogs.put(name, it) }
        }
    }

    override fun setCharacterId(characterId: String) {
        this.characterId.value = characterId
    }
}

class WindowRegistryFactory(
    private val ioDispatcher: CoroutineDispatcher,
    private val soundPlayer: SoundPlayer,
    private val externalScope: CoroutineScope,
    private val settingRepository: ClientSettingRepository,
    private val highlightRepository: HighlightRepository,
    private val nameRepository: NameRepository,
    private val alterationRepository: AlterationRepository,
    private val presetRepository: PresetRepository,
) {
    fun create(): WindowRegistry {
        return WindowRegistryImpl(
            ioDispatcher = ioDispatcher,
            soundPlayer = soundPlayer,
            externalScope = externalScope,
            settingRepository = settingRepository,
            highlightRepository = highlightRepository,
            nameRepository = nameRepository,
            alterationRepository = alterationRepository,
            presetRepository = presetRepository,
        )
    }
}
