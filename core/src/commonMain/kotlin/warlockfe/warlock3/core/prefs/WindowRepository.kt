package warlockfe.warlock3.core.prefs

import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.dao.WindowSettingsDao
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.window.Window
import warlockfe.warlock3.core.window.WindowLocation
import warlockfe.warlock3.core.window.WindowType

@OptIn(ExperimentalCoroutinesApi::class)
class WindowRepository(
    private val windowSettingsDao: WindowSettingsDao,
    externalScope: CoroutineScope,
) {

    private val _windows = MutableStateFlow(
        mapOf(
            "main" to Window(
                name = "main",
                title = "Main",
                subtitle = "",
                location = WindowLocation.MAIN,
                windowType = WindowType.STREAM,
                position = 0,
                width = null,
                height = null,
                textColor = WarlockColor.Unspecified,
                backgroundColor = WarlockColor.Unspecified,
                fontFamily = null,
                fontSize = null,
                showTimestamps = false,
            )
        )
    )
    val windows = _windows.asStateFlow()

    private val characterId = MutableStateFlow<String?>(null)
    private val windowsSettings = characterId.flatMapLatest { characterId ->
        if (characterId != null) {
            windowSettingsDao.observeByCharacter(characterId)
        } else {
            flow {

            }
        }
    }
        .stateIn(externalScope, SharingStarted.Eagerly, emptyList())

    init {
        windowsSettings
            .onEach { windowSettings ->
                windowSettings.forEach { windowSetting ->
                    _windows.update { windows ->
                        val existingWindows = windows.toMutableMap()
                        val name = windowSetting.name
                        windows[name]?.let { window ->
                            existingWindows[name] = window.copy(
                                location = windowSetting.location ?: window.location,
                                position = windowSetting.position ?: window.position,
                                width = windowSetting.width,
                                height = windowSetting.height,
                                textColor = windowSetting.textColor,
                                backgroundColor = windowSetting.backgroundColor,
                                fontFamily = windowSetting.fontFamily,
                                fontSize = windowSetting.fontSize,
                            )
                        }
                        existingWindows.toPersistentMap()
                    }
                }

            }
            .launchIn(externalScope)
    }

    fun setCharacterId(characterId: String) {
        this.characterId.value = characterId
    }

    fun setWindowTitle(
        name: String,
        title: String,
        subtitle: String?,
        windowType: WindowType,
        showTimestamps: Boolean,
    ) {
        _windows.update { windows ->
            val existingWindow = windows[name]
            val existingWindows = windows.toMutableMap()
            existingWindows[name] = existingWindow
                ?.copy(title = title, subtitle = subtitle, showTimestamps = showTimestamps)
                ?: windowsSettings.value.firstOrNull { it.name == name }?.let { settings ->
                    Window(
                        name = name,
                        title = title,
                        subtitle = subtitle,
                        location = settings.location,
                        windowType = windowType,
                        position = settings.position,
                        width = settings.width,
                        height = settings.height,
                        textColor = settings.textColor,
                        backgroundColor = settings.backgroundColor,
                        fontFamily = settings.fontFamily,
                        fontSize = settings.fontSize,
                        showTimestamps = showTimestamps,
                    )
                }
                ?: Window(
                    name = name,
                    title = title,
                    subtitle = subtitle,
                    location = null,
                    windowType = windowType,
                    position = null,
                    width = null,
                    height = null,
                    textColor = WarlockColor.Unspecified,
                    backgroundColor = WarlockColor.Unspecified,
                    fontFamily = null,
                    fontSize = null,
                    showTimestamps = showTimestamps,
                )
            existingWindows
        }
    }

    fun observeOpenWindows(characterId: String): Flow<Set<String>> {
        return windowSettingsDao.observeOpenWindows(characterId)
            .map { it.toSet() }
    }

    suspend fun openWindow(characterId: String, name: String) {
        withContext(NonCancellable) {
            windowSettingsDao.openWindow(characterId, name)
        }
    }

    suspend fun closeWindow(characterId: String, name: String) {
        withContext(NonCancellable) {
            windowSettingsDao.closeWindow(characterId, name)
        }
    }

    suspend fun moveWindow(characterId: String, name: String, location: WindowLocation) {
        withContext(NonCancellable) {
            windowSettingsDao.moveWindow(characterId, name, location)
        }
    }

    suspend fun setWindowWidth(characterId: String, name: String, width: Int) {
        withContext(NonCancellable) {
            windowSettingsDao.updateWidth(characterId = characterId, name = name, width = width)
        }
    }

    suspend fun setWindowHeight(characterId: String, name: String, height: Int) {
        withContext(NonCancellable) {
            windowSettingsDao.updateHeight(characterId = characterId, name = name, height = height)
        }
    }

    suspend fun setStyle(characterId: String, name: String, style: StyleDefinition) {
        withContext(NonCancellable) {
            windowSettingsDao.setStyle(
                characterId = characterId,
                name = name,
                textColor = style.textColor,
                backgroundColor = style.backgroundColor,
                fontFamily = style.fontFamily,
                fontSize = style.fontSize,
            )
        }
    }

    suspend fun switchPositions(characterId: String, location: WindowLocation, pos1: Int, pos2: Int) {
        withContext(NonCancellable) {
            windowSettingsDao.switchPositions(
                characterId = characterId,
                location = location,
                curpos = pos1,
                newpos = pos2,
            )
        }
    }
}

class WindowRepositoryFactory(
    private val windowSettingsDao: WindowSettingsDao,
    private val externalScope: CoroutineScope,
) {
    fun create(): WindowRepository = WindowRepository(
        windowSettingsDao = windowSettingsDao,
        externalScope = externalScope,
    )
}
