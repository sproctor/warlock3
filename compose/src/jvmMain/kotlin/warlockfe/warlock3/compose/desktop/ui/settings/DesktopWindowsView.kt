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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.components.DEFAULT_PANEL_SCALE
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.compose.ui.settings.WindowSettingsLiveContext
import warlockfe.warlock3.compose.ui.settings.WindowsViewModel
import warlockfe.warlock3.compose.util.LocalDarkTheme
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.toPresets
import warlockfe.warlock3.core.client.GameCharacter
import warlockfe.warlock3.core.text.StyleLayer
import warlockfe.warlock3.core.text.resolve
import warlockfe.warlock3.core.text.toLayer
import warlockfe.warlock3.core.text.toStyleDefinition

/** Per-window color/font settings for the selected character. */
@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
fun DesktopWindowsView(
    viewModel: WindowsViewModel,
    characters: List<GameCharacter>,
    windowLiveContext: WindowSettingsLiveContext?,
    initialWindowTarget: String?,
    modifier: Modifier = Modifier,
) {
    val currentCharacter = viewModel.currentCharacter.collectAsState().value
    if (currentCharacter == null) {
        Text("No characters created", modifier = modifier.padding(16.dp))
        return
    }

    // The character's resolved default ("base") text style, used as the fallback shown for unset window
    // colors: character base over global base over the skin's default, matching WindowRegistryImpl's
    // baseStyle cascade (not the folded-away presets["default"], which is empty after this PR's own
    // load-time migration, and not single-scope - it must include the global base too).
    val skin = LocalSkin.current
    val isDark = LocalDarkTheme.current
    val skinDefault = remember(skin, isDark) { skin.toPresets(isDark)["default"]?.toLayer() ?: StyleLayer() }
    val charBase by viewModel.characterBaseStyle.collectAsState()
    val globalBase by viewModel.globalBaseStyle.collectAsState()
    // The panel scale a window's override falls back to. It has to come from the character being
    // edited (which the selector above can change), not from LocalPanelDefaults - that belongs to the
    // connected game's composition, which is a different character, or absent entirely.
    val characterPanelScale by viewModel.characterPanelScale.collectAsState()
    val defaultStyle =
        remember(charBase, globalBase, skinDefault) {
            resolve(listOf(charBase, globalBase, skinDefault)).toStyleDefinition()
        }

    Column(modifier.fillMaxSize()) {
        DesktopSettingsCharacterSelector(
            selectedCharacter = currentCharacter,
            characters = characters,
            onSelect = viewModel::selectCharacter,
        )
        Spacer(Modifier.height(16.dp))

        WarlockScrollableColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            DesktopWindowsSettingsSection(
                characterId = currentCharacter.id,
                characterPanelScale = characterPanelScale ?: DEFAULT_PANEL_SCALE,
                viewModel = viewModel,
                defaultStyle = defaultStyle,
                liveContext = windowLiveContext,
                initialWindowTarget = initialWindowTarget,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
