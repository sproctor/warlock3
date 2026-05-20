package warlockfe.warlock3.compose.util

import androidx.compose.ui.graphics.Color
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.core.util.toWarlockColor

internal data class ColorGroup(
    val text: Color,
    val bar: Color,
    val background: Color,
)

internal fun SkinObject?.getColorGroup(): ColorGroup =
    ColorGroup(
        text = this?.color?.toWarlockColor().toColor(),
        bar = this?.bar?.toWarlockColor().toColor(),
        background = this?.background?.toWarlockColor().toColor(),
    )