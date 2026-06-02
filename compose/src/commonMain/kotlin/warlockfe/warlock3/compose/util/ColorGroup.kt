package warlockfe.warlock3.compose.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.model.forMode
import warlockfe.warlock3.core.util.toWarlockColor

internal data class ColorGroup(
    val text: Color,
    val bar: Color,
    val background: Color,
)

@Composable
internal fun SkinObject?.getColorGroup(): ColorGroup {
    val isDark = LocalDarkTheme.current
    return ColorGroup(
        text =
            this
                ?.color
                .forMode(isDark)
                ?.toWarlockColor()
                .toColor(),
        bar =
            this
                ?.bar
                .forMode(isDark)
                ?.toWarlockColor()
                .toColor(),
        background =
            this
                ?.background
                .forMode(isDark)
                ?.toWarlockColor()
                .toColor(),
    )
}
