package warlockfe.warlock3.compose.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.core.text.StyleDefinition

@Composable
fun FontPickerDialog(
    currentStyle: StyleDefinition,
    onCloseRequest: () -> Unit,
    onSaveClicked: (FontUpdate) -> Unit,
) {
    val initialFontSize = currentStyle.fontSize ?: MaterialTheme.typography.bodyMedium.fontSize.value
    val size = rememberTextFieldState(initialFontSize.toString())
    var newFontFamily by remember(currentStyle.fontFamily) {
        mutableStateOf(currentStyle.fontFamily ?: "Default")
    }
    var systemFontFamilies by remember { mutableStateOf(emptyList<FontFamilyInfo>())}

    LaunchedEffect(Unit) {
        systemFontFamilies = loadSystemFonts()
    }
    AlertDialog(
        onDismissRequest = onCloseRequest,
        title = { Text("Choose a font") },
        confirmButton = {
            Button(onClick = {
                onSaveClicked(
                    FontUpdate(
                        size = size.text.toString().toFloatOrNull(),
                        newFontFamily
                    )
                )
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onCloseRequest) {
                Text("Cancel")
            }
        },
        text = {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(state = size)

                ScrollableColumn(
                    modifier = Modifier
                        .border(
                            width = Dp.Hairline,
                            color = MaterialTheme.colorScheme.outline,
                            shape = MaterialTheme.shapes.medium,
                        )
                        .clip(MaterialTheme.shapes.medium)
                        .fillMaxWidth()
                ) {

                    (genericFontFamilies + systemFontFamilies).forEach { fontFamily ->
                        ListItem(
                            modifier = Modifier
                                .clickable {
                                    newFontFamily = fontFamily.familyName
                                },
                            colors =
                                if (fontFamily.familyName == newFontFamily) {
                                    ListItemDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                    )
                                } else {
                                    ListItemDefaults.colors()
                                },
                            headlineContent = {
                                Text(
                                    text = "The quick brown fox jumps over the lazy dog",
                                    fontFamily = fontFamily.fontFamily,
                                    maxLines = 1,
                                )
                            },
                            supportingContent = { Text(text = fontFamily.familyName) }
                        )
                    }
                }
                Button(onClick = { onSaveClicked(FontUpdate(null, null)) }) {
                    Text("Reset to defaults")
                }
            }
        }
    )
}

internal data class FontFamilyInfo(
    val familyName: String,
    val fontFamily: FontFamily,
)

data class FontUpdate(val size: Float?, val fontFamily: String?)

private val genericFontFamilies = listOf(
    FontFamilyInfo("Default", FontFamily.Default),
    FontFamilyInfo("Serif", FontFamily.Serif),
    FontFamilyInfo("SansSerif", FontFamily.SansSerif),
    FontFamilyInfo("Monospace", FontFamily.Monospace),
    FontFamilyInfo("Cursive", FontFamily.Cursive),
)

internal expect suspend fun loadSystemFonts(): List<FontFamilyInfo>
