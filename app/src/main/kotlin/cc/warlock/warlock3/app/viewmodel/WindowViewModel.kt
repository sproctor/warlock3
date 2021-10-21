package cc.warlock.warlock3.app.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import cc.warlock.warlock3.core.window.Window
import cc.warlock.warlock3.core.window.WindowRegistry
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

class WindowViewModel(
    val name: String,
    client: StormfrontClient,
    val window: Flow<Window?>
) {
    private val _backgroundColor = mutableStateOf(Color.DarkGray)
    val backgroundColor: State<Color> = _backgroundColor

    private val _textColor = mutableStateOf(Color.White)
    val textColor: State<Color> = _textColor

    val components = client.components

    val lines = client.getStream(name).lines
}