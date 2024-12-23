package warlockfe.warlock3.core.prefs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.dao.WindowSettingsDao
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.window.Window
import warlockfe.warlock3.core.window.WindowLocation

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
                position = 0,
                width = null,
                height = null,
                textColor = WarlockColor.Unspecified,
                backgroundColor = WarlockColor.Unspecified,
                fontFamily = null,
                fontSize = null,
            )
        )
    )
    val windows = _windows.asStateFlow()

    private val characterId = MutableStateFlow<String?>(null)

    init {
        characterId.flatMapLatest { characterId ->
            if (characterId != null) {
                windowSettingsDao.observeByCharacter(characterId)
            } else {
                flow {

                }
            }
        }
            .onEach { windowSettings ->
                windowSettings.forEach {
                    val existingWindow = windows.value[it.name]

                    _windows.value += Pair(
                        it.name,
                        existingWindow?.copy(
                            location = it.location ?: existingWindow.location,
                            position = it.position,
                            width = it.width,
                            height = it.height,
                            textColor = it.textColor,
                            backgroundColor = it.backgroundColor,
                            fontFamily = it.fontFamily,
                            fontSize = it.fontSize,
                        )
                            ?: Window(
                                name = it.name,
                                title = it.name,
                                subtitle = null,
                                location = it.location,
                                position = it.position,
                                width = it.width,
                                height = it.height,
                                textColor = it.textColor,
                                backgroundColor = it.backgroundColor,
                                fontFamily = it.fontFamily,
                                fontSize = it.fontSize,
                            )
                    )
                }

            }
            .launchIn(externalScope)
    }

    fun setCharacterId(characterId: String) {
        this.characterId.value = characterId
    }

    fun setWindowTitle(name: String, title: String, subtitle: String?) {
        val existingWindow = windows.value[name]
        _windows.value += Pair(
            name,
            existingWindow?.copy(title = title, subtitle = subtitle) ?: Window(
                name = name,
                title = title,
                subtitle = subtitle,
                location = null,
                position = null,
                width = null,
                height = null,
                textColor = WarlockColor.Unspecified,
                backgroundColor = WarlockColor.Unspecified,
                fontFamily = null,
                fontSize = null,
            )
        )
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
