package warlockfe.warlock3.compose.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.components.DesktopColorTextField
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockListItem
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.desktop.shim.WarlockTextField
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.models.NameEntity
import warlockfe.warlock3.core.prefs.repositories.NameRepositoryImpl
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.toHexString
import warlockfe.warlock3.core.util.toWarlockColor
import kotlin.uuid.Uuid

@Composable
fun DesktopNamesView(
    currentCharacter: GameCharacter?,
    allCharacters: List<GameCharacter>,
    nameRepository: NameRepositoryImpl,
    modifier: Modifier = Modifier,
) {
    var selectedCharacter by remember(currentCharacter) { mutableStateOf(currentCharacter) }
    val currentCharacterId = selectedCharacter?.id
    val names by if (currentCharacterId == null) {
        nameRepository.observeGlobal()
    } else {
        nameRepository.observeByCharacter(currentCharacterId)
    }.collectAsState(emptyList())
    var editingName by remember { mutableStateOf<NameEntity?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize()) {
        DesktopSettingsCharacterSelector(
            selectedCharacter = selectedCharacter,
            characters = allCharacters,
            onSelect = { selectedCharacter = it },
            allowGlobal = true,
        )
        Spacer(Modifier.height(16.dp))
        Text("Names")
        Spacer(Modifier.height(8.dp))
        WarlockScrollableColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            names.forEach { name ->
                WarlockListItem(
                    leading = {
                        val contentColor = name.textColor.toColor()
                        Box(
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .background(
                                        color = name.backgroundColor.toColor(),
                                        shape = RoundedCornerShape(4.dp),
                                    ).border(1.dp, JewelTheme.globalColors.borders.normal, RoundedCornerShape(4.dp)),
                        )
                        @Suppress("UNUSED_EXPRESSION")
                        contentColor
                    },
                    headline = { Text(name.text) },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            WarlockOutlinedButton(
                                onClick = { editingName = name },
                                text = "Edit",
                            )
                            WarlockOutlinedButton(
                                onClick = {
                                    scope.launch { nameRepository.deleteById(name.id) }
                                },
                                text = "Delete",
                            )
                        }
                    },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            WarlockButton(
                onClick = {
                    editingName =
                        NameEntity(
                            id = Uuid.random(),
                            text = "",
                            characterId = currentCharacterId ?: "global",
                            textColor = WarlockColor.Unspecified,
                            backgroundColor = WarlockColor.Unspecified,
                            bold = false,
                            italic = false,
                            underline = false,
                            fontFamily = null,
                            fontSize = null,
                            sound = null,
                        )
                },
                text = "New name",
            )
        }
    }
    editingName?.let { name ->
        DesktopEditNameDialog(
            name = name,
            saveName = { newName ->
                scope.launch {
                    nameRepository.save(newName)
                    editingName = null
                }
            },
            onClose = { editingName = null },
        )
    }
}

@Composable
private fun DesktopEditNameDialog(
    name: NameEntity,
    saveName: (NameEntity) -> Unit,
    onClose: () -> Unit,
) {
    val text = rememberTextFieldState(name.text)
    val textColor = rememberTextFieldState(name.textColor.toHexString() ?: "")
    val backgroundColor = rememberTextFieldState(name.backgroundColor.toHexString() ?: "")
    val sound = rememberTextFieldState(name.sound ?: "")

    WarlockDialog(
        title = "Edit name",
        onCloseRequest = onClose,
        width = 560.dp,
        height = 480.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Name")
            WarlockTextField(state = text, modifier = Modifier.fillMaxWidth())
            val hasLowercase = text.text.firstOrNull()?.isLowerCase() == true
            if (hasLowercase) {
                Text("First letter of name is lowercase")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopColorTextField(
                    modifier = Modifier.weight(1f),
                    label = "Text color",
                    state = textColor,
                )
                DesktopColorTextField(
                    modifier = Modifier.weight(1f),
                    label = "Background color",
                    state = backgroundColor,
                )
            }

            val soundLauncher =
                rememberFilePickerLauncher { file ->
                    if (file != null) {
                        sound.setTextAndPlaceCursorAtEnd(file.absolutePath())
                    }
                }
            Text("Sound file")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                WarlockTextField(state = sound, modifier = Modifier.weight(1f))
                Spacer(Modifier.size(4.dp))
                WarlockOutlinedButton(onClick = { soundLauncher.launch() }, text = "Browse")
            }

            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
            ) {
                WarlockOutlinedButton(onClick = onClose, text = "Cancel")
                WarlockButton(
                    onClick = {
                        saveName(
                            name.copy(
                                text = text.text.toString(),
                                textColor = textColor.text.toString().toWarlockColor() ?: WarlockColor.Unspecified,
                                backgroundColor = backgroundColor.text.toString().toWarlockColor() ?: WarlockColor.Unspecified,
                                sound = sound.text.toString().ifBlank { null },
                            ),
                        )
                    },
                    text = "OK",
                )
            }
        }
    }
}
