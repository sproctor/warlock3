package warlockfe.warlock3.compose.ui.window

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import warlockfe.warlock3.core.prefs.models.WindowSettings
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyleLayer
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.specifiedOrNull
import warlockfe.warlock3.core.text.toBackground
import warlockfe.warlock3.core.window.WindowInfo

@Stable
data class WindowUiState(
    val name: String,
    val windowInfo: MutableState<WindowInfo?>,
    val style: StyleDefinition,
    // Per-window font overrides; null falls back to the character default / monospace font.
    val font: FontConfig? = null,
    val monoFont: FontConfig? = null,
    val width: Int?,
    val height: Int?,
    val data: WindowData?,
    val nameFilter: Boolean = false,
)

sealed interface WindowData

data class StreamWindowData(
    val stream: ComposeTextStream,
) : WindowData

data class DialogWindowData(
    val dialogData: ComposeDialogState,
) : WindowData

/**
 * The window's color + weight/italic/underline styling. Fonts are carried separately on [WindowUiState].
 * A skin-referenced color resolves against [palette] (the skin's palette at the time of the call); a
 * ref to a slot the palette doesn't define falls back to the last-resolved color stored beside it.
 */
fun WindowSettings.getStyle(palette: Map<String, WarlockColor>): StyleDefinition =
    StyleDefinition(
        textColor = textColorRef?.let { palette[it] } ?: textColor,
        backgroundColor = backgroundColorRef?.let { palette[it] } ?: backgroundColor,
        bold = bold || (weight?.let { it >= 600 } == true),
        italic = italic == true,
        underline = underline == true,
    )

/** The window's style as an editable [StyleLayer], carrying the skin-palette refs for the style editor. */
fun WindowSettings.toStyleLayer(): StyleLayer =
    StyleLayer(
        textColor = textColor.specifiedOrNull(),
        background = backgroundColor.toBackground(),
        weight = weight ?: if (bold) 700 else null,
        italic = italic,
        underline = underline,
        textColorRef = textColorRef,
        backgroundRef = backgroundColorRef,
    )
