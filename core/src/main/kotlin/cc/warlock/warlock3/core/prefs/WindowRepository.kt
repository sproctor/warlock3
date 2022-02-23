package cc.warlock.warlock3.core.prefs

import cc.warlock.warlock3.core.prefs.sql.OpenWindow
import cc.warlock.warlock3.core.prefs.sql.OpenWindowQueries
import cc.warlock.warlock3.core.window.Window
import cc.warlock.warlock3.core.window.WindowLocation
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
class WindowRepository(
    private val openWindowQueries: OpenWindowQueries,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val defaultLocations = mapOf("main" to WindowLocation.MAIN)

    private val _windows = MutableStateFlow(
        mapOf(
            "main" to Window(
                name = "main",
                title = "Main",
                subtitle = "",
                styleIfClosed = null,
                ifClosed = null,
                location = WindowLocation.MAIN,
            )
        )
    )
    val windows = _windows.asStateFlow()

    fun getWindow(name: String): Window? {
        return windows.value[name]
    }

    fun addWindow(window: Window) {
        _windows.value = windows.value +
                (window.name to window.copy(location = defaultLocations[window.name] ?: window.location))
    }

    fun observeOpenWindows(characterId: String): Flow<Set<String>> {
        return openWindowQueries.getByCharacter(characterId)
            .asFlow()
            .mapToList(ioDispatcher)
            .map { it.toSet() }
    }

    fun openWindow(characterId: String, name: String) {
        GlobalScope.launch {
            openWindowQueries.put(
                OpenWindow(
                    characterId = characterId,
                    name = name,
                )
            )
        }
    }

    fun closeWindow(characterId: String, name: String) {
        GlobalScope.launch {
            openWindowQueries.delete(
                characterId = characterId,
                name = name,
            )
        }
    }
}