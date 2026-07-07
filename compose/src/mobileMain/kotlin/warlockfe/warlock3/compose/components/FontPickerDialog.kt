package warlockfe.warlock3.compose.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import warlockfe.warlock3.core.text.FontConfig

@Composable
fun FontPickerDialog(
    current: FontConfig?,
    onCloseRequest: () -> Unit,
    onSaveClick: (FontUpdate) -> Unit,
    monospaceOnly: Boolean = false,
) {
    val state = rememberFontPickerState(current, MaterialTheme.typography.bodyMedium.fontSize.value, monospaceOnly)

    AlertDialog(
        onDismissRequest = onCloseRequest,
        title = { Text("Choose a font") },
        confirmButton = { Button(onClick = { onSaveClick(state.toFontUpdate()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onCloseRequest) { Text("Cancel") } },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Single live preview of the combined family + size + weight.
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .border(
                                Dp.Hairline,
                                MaterialTheme.colorScheme.outline,
                                MaterialTheme.shapes.medium,
                            ).padding(horizontal = 12.dp),
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
                OutlinedButton(
                    onClick = { state.familyPickerOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Family: ${state.selectedFamily}")
                }

                var weightMenuExpanded by remember { mutableStateOf(false) }
                val weightLabel = fontWeightOptions.firstOrNull { it.weight == state.weight }?.label ?: "Default"
                Box {
                    OutlinedButton(onClick = { weightMenuExpanded = true }) {
                        Text("Weight: $weightLabel")
                    }
                    DropdownMenu(
                        expanded = weightMenuExpanded,
                        onDismissRequest = { weightMenuExpanded = false },
                    ) {
                        fontWeightOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    state.weight = option.weight
                                    weightMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Size")
                    OutlinedButton(onClick = { state.stepSize(-FONT_SIZE_STEP) }) { Text("-") }
                    OutlinedTextField(state = state.sizeFieldState, modifier = Modifier.width(96.dp))
                    OutlinedButton(onClick = { state.stepSize(FONT_SIZE_STEP) }) { Text("+") }
                }

                TextButton(onClick = { onSaveClick(FontUpdate(null, null)) }) {
                    Text("Reset to defaults")
                }
            }
        },
    )

    if (state.familyPickerOpen) {
        FontFamilyPickerDialog(state = state)
    }
}

@Composable
private fun FontFamilyPickerDialog(state: FontPickerState) {
    AlertDialog(
        onDismissRequest = { state.familyPickerOpen = false },
        title = { Text("Choose a font family") },
        confirmButton = {
            TextButton(onClick = { state.familyPickerOpen = false }) { Text("Close") }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    state = state.queryState,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search fonts") },
                )
                if (!state.fontsLoaded) {
                    Text("Loading system fonts...")
                }
                ScrollableColumn(
                    modifier =
                        Modifier
                            .border(
                                width = Dp.Hairline,
                                color = MaterialTheme.colorScheme.outline,
                                shape = MaterialTheme.shapes.medium,
                            ).clip(MaterialTheme.shapes.medium)
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                ) {
                    state.filteredFamilies.forEach { family ->
                        ListItem(
                            modifier =
                                Modifier.clickable {
                                    state.selectedFamily = family.familyName
                                    state.familyPickerOpen = false
                                },
                            colors =
                                if (family.familyName == state.selectedFamily) {
                                    ListItemDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                    )
                                } else {
                                    ListItemDefaults.colors()
                                },
                            headlineContent = {
                                Text(
                                    text = "The quick brown fox jumps over the lazy dog",
                                    fontFamily = family.fontFamily,
                                    maxLines = 1,
                                )
                            },
                            supportingContent = { Text(text = family.familyName) },
                        )
                    }
                }
            }
        },
    )
}
