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
import warlockfe.warlock3.core.prefs.config.GLOBAL_CHARACTER_ID
import warlockfe.warlock3.core.prefs.repositories.CharacterSettingsRepository
import warlockfe.warlock3.core.prefs.repositories.WindowSettingsRepository
import warlockfe.warlock3.core.text.StyleLayer
import warlockfe.warlock3.core.text.resolve
import warlockfe.warlock3.core.text.toLayer
import warlockfe.warlock3.core.text.toStyleDefinition

/** Per-window color/font settings for the selected character. */
@Composable
fun WindowsView(
    initialCharacter: GameCharacter?,
    characters: List<GameCharacter>,
    characterSettingsRepository: CharacterSettingsRepository,
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

    // The character's resolved default ("base") text style, used as the fallback shown for unset window
    // colors: character base over global base over the skin's default, matching WindowRegistryImpl's
    // baseStyle cascade (not the folded-away presets["default"], which is empty after this PR's own
    // load-time migration, and not single-scope - it must include the global base too).
    val skin = LocalSkin.current
    val isDark = LocalDarkTheme.current
    val skinDefault = remember(skin, isDark) { skin.toPresets(isDark)["default"]?.toLayer() ?: StyleLayer() }
    val charBase by remember(currentCharacter.id) {
        characterSettingsRepository.observeBaseStyle(currentCharacter.id)
    }.collectAsState(StyleLayer())
    val globalBase by remember { characterSettingsRepository.observeBaseStyle(GLOBAL_CHARACTER_ID) }.collectAsState(StyleLayer())
    val defaultStyle =
        remember(charBase, globalBase, skinDefault) {
            resolve(listOf(charBase, globalBase, skinDefault)).toStyleDefinition()
        }

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
