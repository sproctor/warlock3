package warlockfe.warlock3.compose.desktop.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class WarlockColors(
    val prompt: Color,
    val command: Color,
    val speech: Color,
    val whisper: Color,
    val thought: Color,
    val watching: Color,
    val roomName: Color,
    val link: Color,
    val highlight: Color,
    val presenceConnected: Color,
    val presenceDisconnected: Color,
) {
    companion object {
        fun light(): WarlockColors =
            WarlockColors(
                prompt = Color(0xFF707070),
                command = Color(0xFF1565C0),
                speech = Color(0xFF005ABE),
                whisper = Color(0xFF7B1FA2),
                thought = Color(0xFF7E57C2),
                watching = Color(0xFFC62828),
                roomName = Color(0xFF1E88E5),
                link = Color(0xFF2962FF),
                highlight = Color(0xFFFFB300),
                presenceConnected = Color(0xFF2E7D32),
                presenceDisconnected = Color(0xFFC62828),
            )

        fun dark(): WarlockColors =
            WarlockColors(
                prompt = Color(0xFFB0B0B0),
                command = Color(0xFF82B1FF),
                speech = Color(0xFF80B3FF),
                whisper = Color(0xFFCE93D8),
                thought = Color(0xFFB39DDB),
                watching = Color(0xFFEF9A9A),
                roomName = Color(0xFF82B1FF),
                link = Color(0xFF82B1FF),
                highlight = Color(0xFFFFD54F),
                presenceConnected = Color(0xFF81C784),
                presenceDisconnected = Color(0xFFE57373),
            )

        fun from(isDark: Boolean): WarlockColors = if (isDark) dark() else light()
    }
}

val LocalWarlockColors =
    staticCompositionLocalOf<WarlockColors> {
        error("WarlockColors not provided. Wrap content in WarlockDesktopTheme.")
    }
