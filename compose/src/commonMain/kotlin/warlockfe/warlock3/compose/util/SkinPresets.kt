package warlockfe.warlock3.compose.util

import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.model.forMode
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.util.getIgnoringCase
import warlockfe.warlock3.core.util.toWarlockColor

/**
 * Minimal built-in style used only as a safety net when a skin omits a "default" preset, so game
 * text stays legible. The canonical defaults live in the skin's "presets" section.
 */
val SAFE_DEFAULT_STYLE =
    StyleDefinition(
        textColor = WarlockColor("#F0F0FF"),
        backgroundColor = WarlockColor("#1E1F22"),
    )

/** Converts a "presets" child [SkinObject] to a [StyleDefinition], resolving colors for the mode. */
fun SkinObject.toStyleDefinition(isDark: Boolean): StyleDefinition =
    StyleDefinition(
        textColor = color.forMode(isDark)?.toWarlockColor() ?: WarlockColor.Unspecified,
        backgroundColor = background.forMode(isDark)?.toWarlockColor() ?: WarlockColor.Unspecified,
        entireLine = entireLine ?: false,
        bold = bold ?: false,
        italic = italic ?: false,
        underline = underline ?: false,
        fontFamily = fontFamily,
        fontSize = fontSize,
        fontWeight = fontWeight,
    )

/** Resolves the skin's "presets" section into a name -> [StyleDefinition] map for the given mode. */
fun Map<String, SkinObject>.toPresets(isDark: Boolean): Map<String, StyleDefinition> =
    (getIgnoringCase("presets")?.children ?: emptyMap())
        .mapValues { (_, preset) -> preset.toStyleDefinition(isDark) }
