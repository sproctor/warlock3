package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.util.LocalDarkTheme
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.toPresets
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.repositories.PresetRepository
import warlockfe.warlock3.core.prefs.repositories.WindowSettingsRepository
import warlockfe.warlock3.core.text.StyleDefinition

/** Per-window color/font settings for the selected character. */
@Composable
fun WindowsView(
    initialCharacter: GameCharacter?,
    characters: List<GameCharacter>,
    presetRepository: PresetRepository,
    windowSettingRepository: WindowSettingsRepository,
    windowLiveContext: WindowSettingsLiveContext?,
    initialWindowTarget: String?,
    modifier: Modifier = Modifier,
) {
    val currentCharacterState =
        remember(initialCharacter, characters) {
            mutableStateOf(initialCharacter ?: characters.firstOrNull())
        }
    val currentCharacter = currentCharacterState.value
    if (currentCharacter == null) {
        Text("No characters created")
        return
    }

    // The character's resolved default text style, used as the fallback shown for unset window colors.
    val savedPresets by remember(currentCharacter.id) {
        presetRepository.observePresetsForCharacter(currentCharacter.id)
    }.collectAsState(emptyMap())
    val skin = LocalSkin.current
    val isDark = LocalDarkTheme.current
    val defaultStyle =
        remember(skin, isDark, savedPresets) { (skin.toPresets(isDark) + savedPresets)["default"] ?: StyleDefinition() }

    Column(modifier.fillMaxSize()) {
        SettingsCharacterSelector(
            selectedCharacter = currentCharacter,
            characters = characters,
            onSelect = { currentCharacterState.value = it },
        )
        Spacer(Modifier.height(16.dp))
        ScrollableColumn(Modifier.weight(1f).fillMaxWidth()) {
            WindowsSettingsSection(
                characterId = currentCharacter.id,
                windowSettingRepository = windowSettingRepository,
                defaultStyle = defaultStyle,
                liveContext = windowLiveContext,
                initialWindowTarget = initialWindowTarget,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
