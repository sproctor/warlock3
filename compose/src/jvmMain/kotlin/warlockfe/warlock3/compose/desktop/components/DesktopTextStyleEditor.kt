package warlockfe.warlock3.compose.desktop.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.components.GENERIC_SAMPLE
import warlockfe.warlock3.compose.components.StyleChip
import warlockfe.warlock3.compose.components.StyleSample
import warlockfe.warlock3.compose.components.backgroundLabel
import warlockfe.warlock3.compose.components.fontLabel
import warlockfe.warlock3.compose.components.fontWeightOptions
import warlockfe.warlock3.compose.desktop.shim.WarlockCheckboxRow
import warlockfe.warlock3.compose.desktop.shim.WarlockDropdownSelect
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
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
 * The shared desktop (Jewel) editor for one text style at one edit scope, reused by presets, the base
 * text, windows, highlights, and names. Each attribute shows its control, a "set here"/"inherited" tag,
 * and a reset that is enabled only when the value was set at [editScope]. Every change folds into
 * [editLayer] via [applyEdit] and is handed back through [onSave] for the caller to persist to that one
 * scope's layer. [sample] (the fully-resolved cascade) drives the effective values shown and the live
 * preview.
 */
@Composable
fun DesktopTextStyleEditor(
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
        DesktopColorPickerDialog(
            initialColor = sample.textColor.toColor(),
            onCloseRequest = { editColor = null },
            onColorSelect = { color ->
                onPick(color)
                editColor = null
            },
        )
    }
    if (editFont) {
        DesktopFontPickerDialog(
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
        DesktopBackgroundPickerDialog(
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
        DesktopColorRefPickerDialog(
            palette = palette,
            onSelectSlot = { edit(StyleEdit.SetTextColorRef(it)) },
            onSelectCustom = { editColor = { color -> edit(StyleEdit.SetTextColor(color)) } },
            onClose = { editTextColor = false },
        )
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StylePreview(sample, windowBackground, sampleLine, baseStyle)

        AttributeRow("Text color", sourced.textColor.source, editScope, onReset = { edit(StyleEdit.Reset(StyleAttribute.TextColor)) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopColorPickerButton(
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
                    Text("tracks $slot", color = LocalContentColor.current.copy(alpha = 0.6f))
                }
            }
        }

        AttributeRow("Background", sourced.background.source, editScope, onReset = { edit(StyleEdit.Reset(StyleAttribute.Background)) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WarlockOutlinedButton(
                    onClick = { editBackground = true },
                    text = backgroundLabel(sample.background),
                )
                editLayer.backgroundRef?.let { slot ->
                    Text("tracks $slot", color = LocalContentColor.current.copy(alpha = 0.6f))
                }
            }
        }

        AttributeRow("Weight", sourced.weight.source, editScope, onReset = { edit(StyleEdit.Reset(StyleAttribute.Weight)) }) {
            WarlockDropdownSelect(
                items = fontWeightOptions,
                selected = fontWeightOptions.firstOrNull { it.weight == sample.weight } ?: fontWeightOptions.first(),
                onSelect = { edit(StyleEdit.SetWeight(it.weight)) },
                itemLabelBuilder = { it.label },
            )
        }

        AttributeRow("Italic", sourced.italic.source, editScope, onReset = { edit(StyleEdit.Reset(StyleAttribute.Italic)) }) {
            WarlockCheckboxRow(
                checked = sample.italic,
                onCheckedChange = { edit(StyleEdit.SetItalic(it)) },
                text = "Italic",
            )
        }

        AttributeRow("Underline", sourced.underline.source, editScope, onReset = { edit(StyleEdit.Reset(StyleAttribute.Underline)) }) {
            WarlockCheckboxRow(
                checked = sample.underline,
                onCheckedChange = { edit(StyleEdit.SetUnderline(it)) },
                text = "Underline",
            )
        }

        if (showEntireLine) {
            AttributeRow(
                "Entire line",
                sourced.entireLine.source,
                editScope,
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
            AttributeRow("Monospace", sourced.monospace.source, editScope, onReset = { edit(StyleEdit.Reset(StyleAttribute.Monospace)) }) {
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
            AttributeRow("Font", sourced.fontFamily.source, editScope, onReset = {
                edit(StyleEdit.Reset(StyleAttribute.FontFamily), StyleEdit.Reset(StyleAttribute.FontSize))
            }) {
                WarlockOutlinedButton(
                    onClick = { editFont = true },
                    text = FontConfig(family = sample.fontFamily, size = sample.fontSize, weight = sample.weight).fontLabel(),
                )
            }
        }
    }
}

/** One attribute's control plus its source tag and a reset enabled only when set at [editScope]. */
@Composable
private fun AttributeRow(
    label: String,
    source: StyleScope?,
    editScope: StyleScope,
    onReset: () -> Unit,
    control: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.width(96.dp))
        control()
        Spacer(Modifier.width(4.dp))
        Text(sourceLabel(source, editScope))
        Spacer(Modifier.weight(1f))
        WarlockOutlinedButton(
            onClick = onReset,
            text = "Reset",
            enabled = source == editScope,
        )
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
                Text("Low contrast ($rounded:1)", color = Color(0xFFCC7A00))
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
