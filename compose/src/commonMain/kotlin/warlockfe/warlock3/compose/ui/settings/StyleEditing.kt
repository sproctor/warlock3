package warlockfe.warlock3.compose.ui.settings

import warlockfe.warlock3.core.prefs.config.NameConfig
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

/** Collapses a single edited [StyleLayer] back to the dense style these sections persist. */
fun StyleLayer.toStyleDefinition(): StyleDefinition = resolve(listOf(this)).toStyleDefinition()

/** A name's color + bold/italic/underline as an editable [StyleLayer] (names carry no fonts). */
fun NameConfig.toStyleLayer(): StyleLayer =
    StyleLayer(
        textColor = textColor.specifiedOrNull(),
        background = backgroundColor.toBackground(),
        weight = if (bold) 700 else null,
        italic = if (italic) true else null,
        underline = if (underline) true else null,
    )

/** Writes an edited [layer] back onto a name's color + bold/italic/underline fields. */
fun NameConfig.withStyle(layer: StyleLayer): NameConfig =
    copy(
        textColor = layer.textColor ?: WarlockColor.Unspecified,
        backgroundColor = layer.background.toWarlockColor(),
        bold = layer.weight == 700,
        italic = layer.italic == true,
        underline = layer.underline == true,
    )
