package warlockfe.warlock3.compose.ui.settings

import androidx.compose.ui.graphics.Color
import warlockfe.warlock3.compose.util.SAFE_DEFAULT_STYLE
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.prefs.config.NameConfig
import warlockfe.warlock3.core.text.Background
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyleLayer
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.resolve
import warlockfe.warlock3.core.text.specifiedOrNull
import warlockfe.warlock3.core.text.toBackground
import warlockfe.warlock3.core.text.toStyleDefinition
import warlockfe.warlock3.core.text.toWarlockColor

// Helpers for the sections that edit one flat style with no cascade (names, highlights) using the shared
// DesktopTextStyleEditor / TextStyleEditor: they store a dense StyleDefinition-shaped style, so the
// editor's StyleLayer is projected to and from that.

/**
 * The effective game window background (the resolved base background cascaded skin -> global -> character)
 * that a style preview/chip should composite against - never the settings panel surface. Shared by every
 * page (presets, highlights, names, windows) so previews and contrast warnings agree with the real game
 * background instead of a hardcoded chrome color.
 */
fun resolvedWindowBackground(baseLayers: List<StyleLayer>): Color =
    when (val bg = resolve(baseLayers).background) {
        is Background.Fill -> bg.color.toColor(default = SAFE_DEFAULT_STYLE.backgroundColor.toColor())
        else -> SAFE_DEFAULT_STYLE.backgroundColor.toColor()
    }

/** Collapses a single edited [StyleLayer] back to the dense style these sections persist. */
fun StyleLayer.toStyleDefinition(): StyleDefinition = resolve(listOf(this)).toStyleDefinition()

/** A name's color + bold/italic/underline + per-item font as an editable [StyleLayer]. */
fun NameConfig.toStyleLayer(): StyleLayer =
    StyleLayer(
        textColor = textColor.specifiedOrNull(),
        background = backgroundColor.toBackground(),
        fontFamily = fontFamily,
        fontSize = fontSize,
        weight = weight ?: if (bold) 700 else null,
        italic = italic,
        underline = underline,
        monospace = monospace,
        textColorRef = textColorRef,
        backgroundRef = backgroundColorRef,
    )

/** Writes an edited [layer] back onto a name's color + bold/italic/underline + per-item font fields. */
fun NameConfig.withStyle(layer: StyleLayer): NameConfig =
    copy(
        textColor = layer.textColor ?: WarlockColor.Unspecified,
        backgroundColor = layer.background.toWarlockColor(),
        bold = layer.weight == 700,
        italic = layer.italic,
        underline = layer.underline,
        monospace = layer.monospace,
        weight = layer.weight?.takeUnless { it == 700 },
        fontFamily = layer.fontFamily,
        fontSize = layer.fontSize,
        textColorRef = layer.textColorRef,
        backgroundColorRef = layer.backgroundRef,
    )
