package warlockfe.warlock3.compose.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.icons.Login
import warlockfe.warlock3.compose.ui.settings.character.CharacterSettingsDialog
import warlockfe.warlock3.core.client.CharacterProxySettings
import warlockfe.warlock3.core.client.GameCharacter

@Composable
fun DashboardView(
    viewModel: DashboardViewModel,
    connectToSGE: () -> Unit,
) {
    Surface {
        Column(
            Modifier.fillMaxSize()
                .padding(16.dp)
        ) {
            Button(
                onClick = connectToSGE,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                enabled = !viewModel.busy,
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(text = "Connect a new character")
            }
            Spacer(Modifier.height(16.dp))
            if (viewModel.message != null) {
                Text(text = viewModel.message!!)
                Spacer(Modifier.height(16.dp))
            }
            if (!viewModel.busy) {
                CharacterList(
                    modifier = Modifier.weight(1f),
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
fun CharacterList(
    modifier: Modifier,
    viewModel: DashboardViewModel,
) {
    var showCharacterSettings: GameCharacter? by remember { mutableStateOf(null) }
    val characters by viewModel.characters.collectAsState(emptyList())
    ScrollableColumn(modifier) {
        Text(text = "Characters", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        characters.forEach { character ->
            ListItem(
                headlineContent = { Text(character.name) },
                supportingContent = { Text(character.gameCode) },
                leadingContent = {
                    IconButton(
                        onClick = {
                            viewModel.connectCharacter(character)
                        },
                    ) {
                        Icon(imageVector = Login, contentDescription = null)
                    }
                },
                trailingContent = {
                    IconButton(
                        onClick = {
                            showCharacterSettings = character
                        },
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                    }
                }
            )
        }
    }
    if (showCharacterSettings != null) {
        var proxySettings: CharacterProxySettings? by remember(showCharacterSettings) {
            mutableStateOf(null)
        }
        if (proxySettings != null) {
            CharacterSettingsDialog(
                proxySettings = proxySettings!!,
                updateProxySettings = {
                    viewModel.updateProxySettings(showCharacterSettings!!.id, it)
                },
                closeDialog = { showCharacterSettings = null },
            )
        }
        LaunchedEffect(showCharacterSettings) {
            showCharacterSettings?.let { characterSettings ->
                proxySettings = viewModel.getProxySettings(characterSettings.id)
            }
        }
    }
}