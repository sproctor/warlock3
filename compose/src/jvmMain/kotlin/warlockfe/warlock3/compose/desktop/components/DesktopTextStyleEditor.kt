package warlockfe.warlock3.compose.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.components.fontLabel
import warlockfe.warlock3.compose.components.fontWeightOptions
import warlockfe.warlock3.compose.desktop.shim.WarlockCheckboxRow
import warlockfe.warlock3.compose.desktop.shim.WarlockDropdownSelect
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.util.createFontFamily
import warlockfe.warlock3.compose.util.toColor
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
import warlockfe.warlock3.core.text.sourceOf

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
) {
    fun edit(vararg edits: StyleEdit) {
        onSave(edits.fold(editLayer) { layer, e -> layer.applyEdit(e) })
    }

    var editColor by remember { mutableStateOf<((WarlockColor) -> Unit)?>(null) }
    var editFont by remember { mutableStateOf(false) }

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

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StylePreview(sample)

        AttributeRow("Text color", sourced.textColor.source, editScope, onReset = { edit(StyleEdit.Reset(StyleAttribute.TextColor)) }) {
            DesktopColorPickerButton(
                text = "Text",
                color = sample.textColor.toColor(),
                onClick = { editColor = { color -> edit(StyleEdit.SetTextColor(color)) } },
            )
        }

        AttributeRow("Background", sourced.background.source, editScope, onReset = { edit(StyleEdit.Reset(StyleAttribute.Background)) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DesktopColorPickerButton(
                    text = "Fill",
                    color = (sample.background as? Background.Fill)?.color.toColor(),
                    onClick = { editColor = { color -> edit(StyleEdit.SetBackground(Background.Fill(color))) } },
                )
                WarlockCheckboxRow(
                    checked = sample.background == Background.None,
                    onCheckedChange = { none -> edit(StyleEdit.SetBackground(if (none) Background.None else Background.Unset)) },
                    text = "None",
                )
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
private fun StylePreview(sample: ResolvedStyle) {
    val background =
        when (val bg = sample.background) {
            is Background.Fill -> bg.color.toColor()
            Background.None -> Color.Transparent
            Background.Unset -> Color(0xFF1E1F22)
        }
    BasicText(
        text = "The quick brown fox jumps over the lazy dog",
        modifier =
            Modifier
                .fillMaxWidth()
                .background(background)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        style =
            TextStyle(
                color = sample.textColor.toColor(default = Color(0xFFF0F0FF)),
                fontWeight = sample.weight?.let { FontWeight(it) },
                fontStyle = if (sample.italic) FontStyle.Italic else null,
                textDecoration = if (sample.underline) TextDecoration.Underline else null,
                fontFamily = sample.fontFamily?.let { createFontFamily(it) },
                fontSize = sample.fontSize?.sp ?: TextUnit.Unspecified,
            ),
    )
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
