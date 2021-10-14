package cc.warlock.warlock3.app.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import kotlinx.coroutines.flow.map

class WindowViewModel(
    val name: String,
    private val client: StormfrontClient
) {
    val window = client.windows.map { windows ->
        windows[name]
    }

    private val _backgroundColor = mutableStateOf(Color.DarkGray)
    val backgroundColor: State<Color> = _backgroundColor

    private val _textColor = mutableStateOf(Color.White)
    val textColor: State<Color> = _textColor

    val components = client.components

    val lines = client.getStream(name).lines
}