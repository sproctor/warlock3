package warlockfe.warlock3.compose.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import warlockfe.warlock3.compose.components.FontFamilyInfo
import warlockfe.warlock3.compose.components.FontUpdate
import warlockfe.warlock3.compose.components.loadSystemFonts
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
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
    val initialFontSize = currentStyle.fontSize ?: JewelTheme.defaultTextStyle.fontSize.value
    val size = rememberTextFieldState(initialFontSize.toString())
    var newFontFamily by remember(currentStyle.fontFamily) {
        mutableStateOf(currentStyle.fontFamily ?: "Default")
    }
    var systemFontFamilies by remember { mutableStateOf(emptyList<FontFamilyInfo>()) }

    LaunchedEffect(Unit) {
        systemFontFamilies = loadSystemFonts()
    }

    val families = remember(systemFontFamilies) { genericFontFamilies + systemFontFamilies }
    val listState = rememberLazyListState()

    WarlockDialog(
        title = "Choose a font",
        onCloseRequest = onCloseRequest,
        width = 500.dp,
        height = 540.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
                Text("Size")
                WarlockTextField(state = size, modifier = Modifier.fillMaxWidth())

            if (systemFontFamilies.isEmpty()) {
                Text("Loading system fonts…")
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
                    items(families, key = { it.familyName }) { family ->
                        val isSelected = family.familyName == newFontFamily
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
                                    .clickable { newFontFamily = family.familyName }
                                    .background(rowBackground)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                WarlockOutlinedButton(
                    onClick = { onSaveClick(FontUpdate(null, null)) },
                    text = "Reset to defaults",
                )
                Spacer(Modifier.weight(1f))
                WarlockOutlinedButton(onClick = onCloseRequest, text = "Cancel")
                WarlockButton(
                    onClick = {
                        onSaveClick(
                            FontUpdate(
                                size = size.text.toString().toFloatOrNull(),
                                newFontFamily,
                            ),
                        )
                    },
                    text = "Save",
                )
            }
        }
    }
}

private val genericFontFamilies =
    listOf(
        FontFamilyInfo("Default", FontFamily.Default),
        FontFamilyInfo("Serif", FontFamily.Serif),
        FontFamilyInfo("SansSerif", FontFamily.SansSerif),
        FontFamilyInfo("Monospace", FontFamily.Monospace),
        FontFamilyInfo("Cursive", FontFamily.Cursive),
    )
