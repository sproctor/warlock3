package warlockfe.warlock3.compose.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import warlockfe.warlock3.compose.components.FONT_PICKER_SAMPLE_TEXT
import warlockfe.warlock3.compose.components.FONT_SIZE_STEP
import warlockfe.warlock3.compose.components.FontPickerState
import warlockfe.warlock3.compose.components.FontUpdate
import warlockfe.warlock3.compose.components.fontWeightOptions
import warlockfe.warlock3.compose.components.rememberFontPickerState
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockDropdownSelect
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockTextField
import warlockfe.warlock3.core.text.StyleDefinition

@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun DesktopFontPickerDialog(
    currentStyle: StyleDefinition,
    onCloseRequest: () -> Unit,
    onSaveClick: (FontUpdate) -> Unit,
) {
    val state = rememberFontPickerState(currentStyle, JewelTheme.defaultTextStyle.fontSize.value)

    WarlockDialog(
        title = "Choose a font",
        onCloseRequest = onCloseRequest,
        width = 460.dp,
        height = 320.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Single live preview of the combined family + size + weight.
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .border(Dp.Hairline, JewelTheme.globalColors.borders.normal, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = FONT_PICKER_SAMPLE_TEXT,
                    fontFamily = state.previewFontFamily,
                    fontSize = state.effectiveSize.sp,
                    fontWeight = state.weight?.let { FontWeight(it) },
                    maxLines = 2,
                )
            }

            // The font family list lives in its own sub-dialog to keep this one uncluttered.
            WarlockOutlinedButton(
                onClick = { state.familyPickerOpen = true },
                text = "Family: ${state.selectedFamily}",
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Weight", modifier = Modifier.width(48.dp))
                WarlockDropdownSelect(
                    items = fontWeightOptions,
                    selected = fontWeightOptions.firstOrNull { it.weight == state.weight } ?: fontWeightOptions.first(),
                    onSelect = { state.weight = it.weight },
                    itemLabelBuilder = { it.label },
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Size", modifier = Modifier.width(48.dp))
                WarlockOutlinedButton(onClick = { state.stepSize(-FONT_SIZE_STEP) }, text = "-")
                WarlockTextField(state = state.sizeFieldState, modifier = Modifier.width(72.dp))
                WarlockOutlinedButton(onClick = { state.stepSize(FONT_SIZE_STEP) }, text = "+")
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WarlockOutlinedButton(
                    onClick = { onSaveClick(FontUpdate(null, null)) },
                    text = "Reset to defaults",
                )
                Spacer(Modifier.weight(1f))
                WarlockOutlinedButton(onClick = onCloseRequest, text = "Cancel")
                WarlockButton(onClick = { onSaveClick(state.toFontUpdate()) }, text = "Save")
            }
        }
    }

    if (state.familyPickerOpen) {
        DesktopFontFamilyPickerDialog(state = state)
    }
}

@Composable
private fun DesktopFontFamilyPickerDialog(state: FontPickerState) {
    val listState = rememberLazyListState()
    WarlockDialog(
        title = "Choose a font family",
        onCloseRequest = { state.familyPickerOpen = false },
        width = 500.dp,
        height = 540.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WarlockTextField(
                state = state.queryState,
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Search fonts",
            )
            if (!state.fontsLoaded) {
                Text("Loading system fonts...")
            }
            VerticallyScrollableContainer(
                scrollState = listState,
                modifier =
                    Modifier
                        .border(
                            width = Dp.Hairline,
                            color = JewelTheme.globalColors.borders.normal,
                            shape = RoundedCornerShape(6.dp),
                        ).clip(RoundedCornerShape(6.dp))
                        .fillMaxWidth()
                        .weight(1f),
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.filteredFamilies, key = { it.familyName }) { family ->
                        val isSelected = family.familyName == state.selectedFamily
                        val rowBackground =
                            if (isSelected) {
                                JewelTheme.globalColors.borders.normal
                                    .copy(alpha = 0.25f)
                            } else {
                                Color.Transparent
                            }
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        state.selectedFamily = family.familyName
                                        state.familyPickerOpen = false
                                    }.background(rowBackground)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "The quick brown fox jumps over the lazy dog",
                                    fontFamily = family.fontFamily,
                                    maxLines = 1,
                                )
                                Text(text = family.familyName)
                            }
                        }
                    }
                }
            }
        }
    }
}
