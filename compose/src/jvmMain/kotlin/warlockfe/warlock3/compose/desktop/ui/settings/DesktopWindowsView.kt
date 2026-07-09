package warlockfe.warlock3.compose.desktop.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.ui.settings.WindowSettingsLiveContext
import warlockfe.warlock3.compose.util.LocalDarkTheme
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.toPresets
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.prefs.repositories.PresetRepository
import warlockfe.warlock3.core.prefs.repositories.WindowSettingsRepository
import warlockfe.warlock3.core.text.StyleDefinition

/** Per-window color/font settings for the selected character. */
@Composable
fun DesktopWindowsView(
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
        Text("No characters created", modifier = modifier.padding(16.dp))
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
        DesktopSettingsCharacterSelector(
            selectedCharacter = currentCharacter,
            characters = characters,
            onSelect = { currentCharacterState.value = it },
        )
        Spacer(Modifier.height(16.dp))

        WarlockScrollableColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            DesktopWindowsSettingsSection(
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
