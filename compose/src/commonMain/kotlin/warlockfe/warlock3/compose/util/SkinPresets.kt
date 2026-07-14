package warlockfe.warlock3.compose.util

import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.model.forMode
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.specifiedOrNull
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
        monospace = monospace ?: false,
    )

/** Resolves the skin's "presets" section into a name -> [StyleDefinition] map for the given mode. */
fun Map<String, SkinObject>.toPresets(isDark: Boolean): Map<String, StyleDefinition> =
    (getIgnoringCase("presets")?.children ?: emptyMap())
        .mapValues { (_, preset) -> preset.toStyleDefinition(isDark) }

/**
 * The named-color palette a skin-referenced style color ([warlockfe.warlock3.core.text.ColorValue.SkinRef])
 * resolves against: each preset's text color under its name, and its background under `<name>Bg`. This is
 * the slot set shown in the color picker's palette and used by `resolveRefs` so a palette pick tracks the
 * skin.
 */
fun Map<String, StyleDefinition>.presetColorPalette(): Map<String, WarlockColor> {
    val palette = mutableMapOf<String, WarlockColor>()
    forEach { (name, style) ->
        style.textColor.specifiedOrNull()?.let { palette[name] = it }
        style.backgroundColor.specifiedOrNull()?.let { palette["${name}Bg"] = it }
    }
    return palette
}

/** [presetColorPalette] resolved for the given mode straight from the skin's "presets" section. */
fun Map<String, SkinObject>.toColorPalette(isDark: Boolean): Map<String, WarlockColor> = toPresets(isDark).presetColorPalette()
