package warlockfe.warlock3.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.compose.shim.WarlockCheckboxRow
import warlockfe.warlock3.compose.shim.WarlockDropdownSelect
import warlockfe.warlock3.compose.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.shim.WarlockSecondaryText
import warlockfe.warlock3.compose.shim.WarlockText
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.compose.util.toWarlockColor
import warlockfe.warlock3.core.text.Background
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.ResolvedStyle
import warlockfe.warlock3.core.text.SourcedStyle
import warlockfe.warlock3.core.text.StyleAttribute
import warlockfe.warlock3.core.text.StyleEdit
import warlockfe.warlock3.core.text.StyleLayer
import warlockfe.warlock3.core.text.StyleScope
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.applyEdit
import warlockfe.warlock3.core.text.contrastRatio
import warlockfe.warlock3.core.text.sourceOf
import warlockfe.warlock3.core.text.specifiedOrNull
import kotlin.math.roundToInt

/**
 * The editor for one text style at one edit scope, reused by presets, the base text, windows,
 * highlights, and names, on desktop and mobile alike. Each attribute shows its control, a "set here"/"inherited" tag,
 * and a reset that is enabled only when the value was set at [editScope]. Every change folds into
 * [editLayer] via [applyEdit] and is handed back through [onSave] for the caller to persist to that one
 * scope's layer. [sample] (the fully-resolved cascade) drives the effective values shown and the live
 * preview.
 */
@Composable
fun TextStyleEditor(
    sourced: SourcedStyle,
    sample: ResolvedStyle,
    editScope: StyleScope,
    editLayer: StyleLayer,
    onSave: (StyleLayer) -> Unit,
    modifier: Modifier = Modifier,
    showFont: Boolean = true,
    showEntireLine: Boolean = false,
    showMonospace: Boolean = false,
    windowBackground: Color = Color(0xFF1E1F22),
    inheritedBackground: Background = Background.Unset,
    palette: Map<String, WarlockColor> = emptyMap(),
    sampleLine: StyleSample = GENERIC_SAMPLE,
    baseStyle: ResolvedStyle? = null,
) {
    fun edit(vararg edits: StyleEdit) {
        onSave(edits.fold(editLayer) { layer, e -> layer.applyEdit(e) })
    }

    var editColor by remember { mutableStateOf<((WarlockColor) -> Unit)?>(null) }
    var editFont by remember { mutableStateOf(false) }
    var editBackground by remember { mutableStateOf(false) }
    var editTextColor by remember { mutableStateOf(false) }

    editColor?.let { onPick ->
        ColorPickerDialog(
            initialColor = sample.textColor.toColor(),
            onCloseRequest = { editColor = null },
            onColorSelect = { color ->
                onPick(color)
                editColor = null
            },
        )
    }
    if (editFont) {
        FontPickerDialog(
            current = FontConfig(family = sample.fontFamily, size = sample.fontSize, weight = sample.weight),
            onCloseRequest = { editFont = false },
            onSaveClick = { update ->
                edit(
                    StyleEdit.SetFontFamily(update.fontFamily),
                    StyleEdit.SetFontSize(update.size),
                    StyleEdit.SetWeight(update.weight),
                )
                editFont = false
            },
        )
    }
    if (editBackground) {
        BackgroundPickerDialog(
            // The sparse edit-scope layer, not the resolved sample: Inherit must show selected only when
            // this scope itself hasn't set a background, even though a lower layer (skin/global) resolves
            // to a Fill.
            current = editLayer.background,
            inheritedBackground = inheritedBackground,
            onSelect = { edit(StyleEdit.SetBackground(it)) },
            onClose = { editBackground = false },
            palette = palette,
            onSelectSlot = { edit(StyleEdit.SetBackgroundRef(it)) },
        )
    }
    if (editTextColor) {
        ColorRefPickerDialog(
            palette = palette,
            onSelectSlot = { edit(StyleEdit.SetTextColorRef(it)) },
            onSelectCustom = { editColor = { color -> edit(StyleEdit.SetTextColor(color)) } },
            onClose = { editTextColor = false },
        )
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StylePreview(sample, windowBackground, sampleLine, baseStyle)

        val attributes =
            buildList {
                attribute("Text color", sourced.textColor.source, onReset = { edit(StyleEdit.Reset(StyleAttribute.TextColor)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ColorPickerButton(
                            text = "Text",
                            color = sample.textColor.toColor(),
                            onClick = {
                                // With a skin palette available, offer palette (skin-tracking) picks; otherwise the
                                // plain color picker stores a frozen literal.
                                if (palette.isEmpty()) {
                                    editColor = { color -> edit(StyleEdit.SetTextColor(color)) }
                                } else {
                                    editTextColor = true
                                }
                            },
                        )
                        editLayer.textColorRef?.let { slot ->
                            WarlockSecondaryText("tracks $slot")
                        }
                    }
                }

                attribute("Background", sourced.background.source, onReset = { edit(StyleEdit.Reset(StyleAttribute.Background)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WarlockOutlinedButton(
                            onClick = { editBackground = true },
                            text = backgroundLabel(sample.background),
                        )
                        editLayer.backgroundRef?.let { slot ->
                            WarlockSecondaryText("tracks $slot")
                        }
                    }
                }

                attribute("Weight", sourced.weight.source, onReset = { edit(StyleEdit.Reset(StyleAttribute.Weight)) }) {
                    WarlockDropdownSelect(
                        items = fontWeightOptions,
                        selected = fontWeightOptions.firstOrNull { it.weight == sample.weight } ?: fontWeightOptions.first(),
                        onSelect = { edit(StyleEdit.SetWeight(it.weight)) },
                        itemLabelBuilder = { it.label },
                    )
                }

                attribute("Italic", sourced.italic.source, onReset = { edit(StyleEdit.Reset(StyleAttribute.Italic)) }) {
                    WarlockCheckboxRow(
                        checked = sample.italic,
                        onCheckedChange = { edit(StyleEdit.SetItalic(it)) },
                        text = "Italic",
                    )
                }

                attribute("Underline", sourced.underline.source, onReset = { edit(StyleEdit.Reset(StyleAttribute.Underline)) }) {
                    WarlockCheckboxRow(
                        checked = sample.underline,
                        onCheckedChange = { edit(StyleEdit.SetUnderline(it)) },
                        text = "Underline",
                    )
                }

                if (showEntireLine) {
                    attribute(
                        "Entire line",
                        sourced.entireLine.source,
                        onReset = { edit(StyleEdit.Reset(StyleAttribute.EntireLine)) },
                    ) {
                        WarlockCheckboxRow(
                            checked = sample.entireLine,
                            onCheckedChange = { edit(StyleEdit.SetEntireLine(it)) },
                            text = "Highlight entire line",
                        )
                    }
                }

                if (showMonospace) {
                    attribute("Monospace", sourced.monospace.source, onReset = { edit(StyleEdit.Reset(StyleAttribute.Monospace)) }) {
                        WarlockCheckboxRow(
                            checked = sample.monospace,
                            onCheckedChange = { edit(StyleEdit.SetMonospace(it)) },
                            text = "Monospace",
                        )
                    }
                }

                if (showFont) {
                    // The font row sets family, size and weight together; its weight and the Weight row share the
                    // one underlying field, so a change in either is reflected in both.
                    attribute("Font", sourced.fontFamily.source, onReset = {
                        edit(StyleEdit.Reset(StyleAttribute.FontFamily), StyleEdit.Reset(StyleAttribute.FontSize))
                    }) {
                        WarlockOutlinedButton(
                            onClick = { editFont = true },
                            text = FontConfig(family = sample.fontFamily, size = sample.fontSize, weight = sample.weight).fontLabel(),
                        )
                    }
                }
            }
        AttributeRows(attributes, editScope)
    }
}

/** One row of the editor: an attribute's label, the control that sets it, and how to reset it. */
private class Attribute(
    val label: String,
    val source: StyleScope?,
    val onReset: () -> Unit,
    val control: @Composable () -> Unit,
)

private fun MutableList<Attribute>.attribute(
    label: String,
    source: StyleScope?,
    onReset: () -> Unit,
    control: @Composable () -> Unit,
) {
    add(Attribute(label, source, onReset, control))
}

/**
 * The attribute rows: each one's control, a source tag, and a reset enabled only when the value was set
 * at [editScope].
 *
 * Each row is one line - label, control, tag, and Reset against the right edge - when every row fits
 * that way, which on desktop they always do. When any row does not (on a phone, where a Material
 * dropdown alone is 280dp wide), every row stacks instead: the label, tag and Reset share a header line
 * and the control drops to its own line below, so Reset is never squeezed or pushed past the edge.
 * The rows decide together so the form reads as one layout rather than a mix of the two.
 */
@Composable
private fun AttributeRows(
    attributes: List<Attribute>,
    editScope: StyleScope,
) {
    Layout(
        contents =
            attributes.flatMap { attribute ->
                listOf(
                    { WarlockText(attribute.label, modifier = Modifier.width(96.dp)) },
                    // Boxed so the control is exactly one measurable, whatever it emits.
                    { Box { attribute.control() } },
                    { WarlockText(sourceLabel(attribute.source, editScope)) },
                    {
                        WarlockOutlinedButton(
                            onClick = attribute.onReset,
                            text = "Reset",
                            enabled = attribute.source == editScope,
                        )
                    },
                )
            },
    ) { slots, constraints ->
        val gap = 8.dp.roundToPx()
        // The spacing the one-line layout has always had: [gap] between items, an extra 4dp before the
        // tag, and at least two gaps before Reset.
        val tagLead = gap + 4.dp.roundToPx() + gap
        val rowGap = 6.dp.roundToPx()
        val stackGap = 4.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)

        class MeasuredRow(
            val label: Placeable,
            val control: Placeable,
            val tag: Measurable,
            val reset: Placeable,
        ) {
            val inlineWidth =
                label.width + gap + control.width + tagLead + tag.maxIntrinsicWidth(loose.maxHeight) + gap + gap + reset.width
        }

        val rows =
            slots.chunked(4).map { (label, control, tag, reset) ->
                MeasuredRow(
                    label = label.single().measure(loose),
                    control = control.single().measure(loose),
                    tag = tag.single(),
                    reset = reset.single().measure(loose),
                )
            }
        val inlineWidth = rows.maxOfOrNull { it.inlineWidth } ?: 0
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else inlineWidth
        val inline = inlineWidth <= width
        val center = Alignment.CenterVertically

        val tags =
            rows.map { row ->
                if (inline) {
                    row.tag.measure(loose)
                } else {
                    // The tag gets what the header has left between the label and Reset, wrapping if it must.
                    val room = (width - row.label.width - gap - gap - row.reset.width).coerceAtLeast(0)
                    row.tag.measure(loose.copy(maxWidth = room))
                }
            }
        val headerHeights =
            rows.mapIndexed { i, row ->
                if (inline) {
                    maxOf(row.label.height, row.control.height, tags[i].height, row.reset.height)
                } else {
                    maxOf(row.label.height, tags[i].height, row.reset.height)
                }
            }
        val rowHeights =
            rows.mapIndexed { i, row ->
                if (inline) headerHeights[i] else headerHeights[i] + stackGap + row.control.height
            }
        val height = (rowHeights.sum() + rowGap * (rows.size - 1).coerceAtLeast(0)).coerceAtLeast(constraints.minHeight)

        layout(width, height) {
            var y = 0
            rows.forEachIndexed { i, row ->
                val tag = tags[i]
                val header = headerHeights[i]
                val resetX = width - row.reset.width
                row.label.placeRelative(0, y + center.align(row.label.height, header))
                row.reset.placeRelative(resetX, y + center.align(row.reset.height, header))
                if (inline) {
                    val controlX = row.label.width + gap
                    row.control.placeRelative(controlX, y + center.align(row.control.height, header))
                    tag.placeRelative(controlX + row.control.width + tagLead, y + center.align(tag.height, header))
                } else {
                    tag.placeRelative(resetX - gap - tag.width, y + center.align(tag.height, header))
                    row.control.placeRelative(0, y + header + stackGap)
                }
                y += rowHeights[i] + rowGap
            }
        }
    }
}

@Composable
private fun StylePreview(
    sample: ResolvedStyle,
    windowBackground: Color,
    sampleLine: StyleSample,
    baseStyle: ResolvedStyle?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StyleChip(
            resolved = sample,
            windowBackground = windowBackground,
            modifier = Modifier.fillMaxWidth().height(40.dp),
            sample = sampleLine,
            baseStyle = baseStyle,
        )
        // Contrast is computed against the effective background - the fill when set, else the window
        // background the text actually sits on. We warn, never block: dim spam channels are a valid choice.
        val textColor = sample.textColor.specifiedOrNull()
        if (textColor != null) {
            val effectiveBackground = (sample.background as? Background.Fill)?.color ?: windowBackground.toWarlockColor()
            val ratio = contrastRatio(textColor, effectiveBackground)
            if (ratio < 3.0) {
                val rounded = (ratio * 10).roundToInt() / 10.0
                WarlockText("Low contrast ($rounded:1)", color = Color(0xFFCC7A00))
            }
        }
    }
}

private fun sourceLabel(
    source: StyleScope?,
    editScope: StyleScope,
): String =
    when {
        source == null -> "default"
        source == editScope -> "set here"
        source == StyleScope.GLOBAL -> "from Global"
        source == StyleScope.SKIN -> "from skin"
        source == StyleScope.CHARACTER -> "from character"
        else -> ""
    }
