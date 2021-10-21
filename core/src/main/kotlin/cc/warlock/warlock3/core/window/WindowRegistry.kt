package cc.warlock.warlock3.core.window

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class WindowRegistry {
    private val defaultLocations = mapOf("main" to WindowLocation.MAIN)

    private val _windows = MutableStateFlow<Map<String, Window>>(
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

    private val _openWindows = MutableStateFlow<Set<String>>(setOf("room"))
    val openWindows = _openWindows.asStateFlow()

    fun getWindow(name: String): Window? {
        return windows.value[name]
    }

    fun isOpen(name: String): Boolean {
        return openWindows.value.contains(name)
    }

    fun addWindow(window: Window) {
        _windows.value = windows.value +
                (window.name to window.copy(location = defaultLocations[window.name] ?: window.location))
    }

    fun openWindow(name: String) {
        _openWindows.value += name
    }

    fun closeWindow(name: String) {
        _openWindows.value -= name
    }
}