package cc.warlock.warlock3.core.prefs

import cc.warlock.warlock3.core.prefs.sql.WindowSettingsQueries
import cc.warlock.warlock3.core.text.StyleDefinition
import cc.warlock.warlock3.core.text.WarlockColor
import cc.warlock.warlock3.core.window.Window
import cc.warlock.warlock3.core.window.WindowLocation
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class WindowRepository(
    private val windowSettingsQueries: WindowSettingsQueries,
    private val ioDispatcher: CoroutineDispatcher,
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
                windowSettingsQueries.getByCharacter(characterId)
                    .asFlow()
                    .mapToList()
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
                            location = it.location,
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
            .launchIn(CoroutineScope(ioDispatcher))
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
        return windowSettingsQueries.getOpenWindows(characterId)
            .asFlow()
            .mapToList(ioDispatcher)
            .map { it.toSet() }
    }

    suspend fun openWindow(characterId: String, name: String) {
        withContext(ioDispatcher) {
            windowSettingsQueries.transaction {
                val openWindows =
                    windowSettingsQueries.getByLocation(characterId, location = WindowLocation.TOP).executeAsList()
                windowSettingsQueries.openWindow(
                    characterId = characterId,
                    name = name,
                    location = WindowLocation.TOP,
                    position = openWindows.size
                )
            }
        }
    }

    suspend fun closeWindow(characterId: String, name: String) {
        withContext(ioDispatcher) {
            windowSettingsQueries.transaction {
                windowSettingsQueries.getByName(characterId = characterId, name = name)
                    .executeAsOneOrNull()?.let { window ->
                        check(window.location != null)
                        check(window.position != null)
                        windowSettingsQueries.closeWindow(
                            characterId = characterId,
                            name = name,
                        )
                        windowSettingsQueries.closeGap(
                            characterId = characterId,
                            location = window.location,
                            position = window.position
                        )
                    }
            }
        }
    }

    suspend fun moveWindow(characterId: String, name: String, location: WindowLocation) {
        withContext(ioDispatcher) {
            windowSettingsQueries.transaction {
                val oldWindow = windowSettingsQueries.getByName(characterId = characterId, name = name).executeAsOne()
                val newPosition = windowSettingsQueries.getByLocation(characterId = characterId, location = location)
                    .executeAsList().size
                windowSettingsQueries.openWindow(characterId, name, location = location, position = newPosition)
                windowSettingsQueries.closeGap(characterId, oldWindow.location, oldWindow.position)
            }
        }
    }

    suspend fun setWindowWidth(characterId: String, name: String, width: Int) {
        withContext(ioDispatcher) {
            windowSettingsQueries.updateWidth(characterId = characterId, name = name, width = width)
        }
    }

    suspend fun setWindowHeight(characterId: String, name: String, height: Int) {
        withContext(ioDispatcher) {
            windowSettingsQueries.updateHeight(characterId = characterId, name = name, height = height)
        }
    }

    suspend fun setStyle(characterId: String, name: String, style: StyleDefinition) {
        withContext(ioDispatcher) {
            windowSettingsQueries.setStyle(
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
        withContext(ioDispatcher) {
            windowSettingsQueries.switchPositions(
                characterId = characterId,
                location = location,
                curpos = pos1,
                newpos = pos2,
            )
        }
    }
}