package warlockfe.warlock3.compose.ui.window

import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import warlockfe.warlock3.core.prefs.ClientSettingRepository
import warlockfe.warlock3.core.window.StreamRegistry
import warlockfe.warlock3.core.window.TextStream

class StreamRegistryImpl(
    private val mainDispatcher: CoroutineDispatcher,
    externalScope: CoroutineScope,
    settingRepository: ClientSettingRepository,
) : StreamRegistry {

    private var streams = persistentHashMapOf<String, ComposeTextStream>()

    private val maxLines = settingRepository
        .observeMaxScrollLines()
        .onEach {
            synchronized(this) {
                streams.values.forEach { stream ->
                    stream.setMaxLines(it)
                }
            }
        }
        .stateIn(
            scope = externalScope,
            started = SharingStarted.Eagerly,
            initialValue = ClientSettingRepository.DEFAULT_MAX_SCROLL_LINES,
        )

    override fun getOrCreateStream(name: String): TextStream {
        synchronized(this) {
            streams[name]?.let { return it }
            return ComposeTextStream(
                id = name,
                maxLines = maxLines.value,
                mainDispatcher = mainDispatcher,
            )
                .also { streams = streams.put(name, it) }
        }
    }

    override fun getStreams(): Collection<TextStream> {
        return streams.values
    }
}

class StreamRegistryFactory(
    private val mainDispatcher: CoroutineDispatcher,
    private val externalScope: CoroutineScope,
    private val settingRepository: ClientSettingRepository,
) {
    fun create(): StreamRegistry {
        return StreamRegistryImpl(
            mainDispatcher = mainDispatcher,
            externalScope = externalScope,
            settingRepository = settingRepository,
        )
    }
}