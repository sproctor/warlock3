package warlockfe.warlock3.compose.desktop.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.components.fontLabel
import warlockfe.warlock3.compose.components.toFontConfig
import warlockfe.warlock3.compose.desktop.components.DesktopFontPickerDialog
import warlockfe.warlock3.compose.desktop.components.DesktopTextStyleEditor
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockCheckboxRow
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.desktop.shim.WarlockTextField
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.arrow_right
import warlockfe.warlock3.compose.ui.settings.WindowSettingsLiveContext
import warlockfe.warlock3.compose.ui.settings.toStyleDefinition
import warlockfe.warlock3.compose.ui.window.getStyle
import warlockfe.warlock3.core.prefs.models.WindowSettings
import warlockfe.warlock3.core.prefs.repositories.WindowSettingsRepository
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyleScope
import warlockfe.warlock3.core.text.resolveSourced
import warlockfe.warlock3.core.text.sampleStyle
import warlockfe.warlock3.core.text.toLayer
import warlockfe.warlock3.core.window.WindowInfo
import warlockfe.warlock3.core.window.WindowLocation

/**
 * Per-window color/font settings for the selected character, grouped into shown windows, windows with
 * saved settings that aren't shown, a collapsed list of other (server-known but unconfigured) windows,
 * and a field to add settings for a window by id. Styling is written straight to
 * [WindowSettingsRepository]; show/hide routes through [liveContext] when the character is connected so
 * the running layout updates, otherwise through the repository (applied on next connect).
 */
@Composable
fun DesktopWindowsSettingsSection(
    characterId: String,
    windowSettingRepository: WindowSettingsRepository,
    defaultStyle: StyleDefinition,
    liveContext: WindowSettingsLiveContext?,
    initialWindowTarget: String?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val windowSettings by remember(characterId) {
        windowSettingRepository.observeWindowSettings(characterId)
    }.collectAsState(emptyList())

    val connected = liveContext?.takeIf { it.connectedCharacterId == characterId }
    val emptyWindowInfo = remember { MutableStateFlow(emptyList<WindowInfo>()) }
    val liveWindows by (connected?.windowInfo ?: emptyWindowInfo).collectAsState()
    val titlesByName = remember(liveWindows) { liveWindows.associateBy { it.name } }

    val settingsByName = remember(windowSettings) { windowSettings.associateBy { it.name } }
    // "main" is always shown and can't be hidden, so it's handled separately (it has no position row).
    val openWindows =
        remember(windowSettings) { windowSettings.filter { it.position != null && it.name != "main" } }
    val savedNotOpen =
        remember(windowSettings) { windowSettings.filter { it.position == null && it.name != "main" } }
    val hiddenWindows =
        remember(liveWindows, settingsByName) {
            liveWindows.filter { it.name !in settingsByName && it.name != "main" }.sortedBy { it.name }
        }
    val openNames = remember(openWindows) { openWindows.map { it.name }.toSet() }

    // Rows expanded to their editor, seeded from the window a "Window settings" action targeted.
    val expanded = remember { mutableStateListOf<String>() }
    // Names typed into the add field for windows we know nothing about yet; they render an empty editor
    // and become real rows once a control is changed (which writes a config entry).
    val pending = remember { mutableStateListOf<String>() }
    var otherExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(initialWindowTarget) {
        val target = initialWindowTarget ?: return@LaunchedEffect
        if (target !in expanded) expanded.add(target)
        if (target !in settingsByName && target !in titlesByName && target !in pending) {
            pending.add(target)
        }
    }

    // One font picker is open at a time across every row (each row's shared style editor owns its own
    // color picker).
    var editFont by remember { mutableStateOf<Triple<FontConfig?, Boolean, (FontConfig?) -> Unit>?>(null) }
    editFont?.let { (current, monoOnly, onSave) ->
        DesktopFontPickerDialog(
            current = current,
            monospaceOnly = monoOnly,
            onCloseRequest = { editFont = null },
            onSaveClick = {
                onSave(it.toFontConfig())
                editFont = null
            },
        )
    }

    val row: @Composable (String, WindowSettings?) -> Unit = { name, settings ->
        DesktopWindowRow(
            name = name,
            title = titlesByName[name]?.title,
            settings = settings,
            defaultStyle = defaultStyle,
            isOpen = name in openNames,
            canToggleShown = name != "main",
            nameFilterAvailable = titlesByName[name]?.nameFilterOption == true || settings?.nameFilter == true,
            expanded = name in expanded,
            onToggleExpand = { if (name in expanded) expanded.remove(name) else expanded.add(name) },
            onSetVisible = { show ->
                if (connected != null) {
                    if (show) connected.openWindow(name) else connected.closeWindow(name)
                } else {
                    scope.launch {
                        if (show) {
                            windowSettingRepository.openWindow(characterId, name, WindowLocation.TOP, openWindows.size)
                        } else {
                            windowSettingRepository.closeWindow(characterId, name)
                        }
                    }
                }
            },
            onSaveStyle = { style -> scope.launch { windowSettingRepository.setStyle(characterId, name, style) } },
            onSaveFont = { font -> scope.launch { windowSettingRepository.setFont(characterId, name, font) } },
            onSaveMonoFont = { font -> scope.launch { windowSettingRepository.setMonoFont(characterId, name, font) } },
            onSaveNameFilter = { value -> scope.launch { windowSettingRepository.setNameFilter(characterId, name, value) } },
            onRevert = {
                scope.launch {
                    windowSettingRepository.setStyle(characterId, name, StyleDefinition())
                    windowSettingRepository.setFont(characterId, name, null)
                    windowSettingRepository.setMonoFont(characterId, name, null)
                }
            },
            onEditFont = { current, monoOnly, onSave -> editFont = Triple(current, monoOnly, onSave) },
        )
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val pendingRows = pending.filter { it !in settingsByName && it !in titlesByName }

        GroupLabel("Shown")
        row("main", settingsByName["main"])
        openWindows.forEach { row(it.name, it) }
        if (savedNotOpen.isNotEmpty() || pendingRows.isNotEmpty()) {
            GroupLabel("Saved settings (not shown)")
            savedNotOpen.forEach { row(it.name, it) }
            pendingRows.forEach { row(it, null) }
        }
        if (hiddenWindows.isNotEmpty()) {
            ExpandHeader(
                label = "Other windows (${hiddenWindows.size})",
                expanded = otherExpanded,
                onClick = { otherExpanded = !otherExpanded },
            )
            if (otherExpanded) {
                hiddenWindows.forEach { row(it.name, null) }
            }
        }

        GroupLabel("Add settings for a window")
        val addState = rememberTextFieldState()
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WarlockTextField(state = addState, modifier = Modifier.weight(1f), placeholder = "Window id")
            WarlockOutlinedButton(
                onClick = {
                    val name = addState.text.toString().trim()
                    if (name.isNotEmpty()) {
                        if (name !in expanded) expanded.add(name)
                        if (name !in settingsByName && name !in titlesByName && name !in pending) {
                            pending.add(name)
                        }
                        addState.clearText()
                    }
                },
                text = "Add settings",
            )
        }
    }
}

@Composable
private fun GroupLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(top = 8.dp),
        color = LocalContentColor.current.copy(alpha = 0.7f),
    )
}

@Composable
private fun ExpandHeader(
    label: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(Res.drawable.arrow_right),
            contentDescription = null,
            modifier = Modifier.size(16.dp).rotate(if (expanded) 90f else 0f),
            colorFilter = ColorFilter.tint(LocalContentColor.current.copy(alpha = 0.7f)),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = LocalContentColor.current.copy(alpha = 0.7f))
    }
}

@Composable
private fun DesktopWindowRow(
    name: String,
    title: String?,
    settings: WindowSettings?,
    defaultStyle: StyleDefinition,
    isOpen: Boolean,
    canToggleShown: Boolean,
    nameFilterAvailable: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onSetVisible: (Boolean) -> Unit,
    onSaveStyle: (StyleDefinition) -> Unit,
    onSaveFont: (FontConfig?) -> Unit,
    onSaveMonoFont: (FontConfig?) -> Unit,
    onSaveNameFilter: (Boolean) -> Unit,
    onRevert: () -> Unit,
    onEditFont: (FontConfig?, Boolean, (FontConfig?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = settings?.getStyle() ?: StyleDefinition()
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggleExpand).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(Res.drawable.arrow_right),
                contentDescription = null,
                modifier = Modifier.size(16.dp).rotate(if (expanded) 90f else 0f),
                colorFilter = ColorFilter.tint(LocalContentColor.current),
            )
            Spacer(Modifier.width(8.dp))
            Text(windowRowLabel(name, title), modifier = Modifier.weight(1f))
            if (canToggleShown) {
                WarlockOutlinedButton(onClick = { onSetVisible(!isOpen) }, text = if (isOpen) "Hide" else "Show")
            }
        }
        if (expanded) {
            Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // A window has no cascade of its own; the character's default text style is the fallback.
                // Feed it to the sample (for a realistic preview) but not to the source tags, so unset
                // attributes read "default" rather than "from skin".
                val windowLayer = style.toLayer()
                DesktopTextStyleEditor(
                    sourced = resolveSourced(listOf(StyleScope.CHARACTER to windowLayer)),
                    sample =
                        sampleStyle(
                            listOf(StyleScope.CHARACTER to windowLayer, StyleScope.SKIN to defaultStyle.toLayer()),
                        ),
                    editScope = StyleScope.CHARACTER,
                    editLayer = windowLayer,
                    onSave = { onSaveStyle(it.toStyleDefinition()) },
                    showFont = false,
                )
                WarlockOutlinedButton(
                    onClick = { onEditFont(settings?.font, false) { onSaveFont(it) } },
                    text = "Font: ${settings?.font.fontLabel()}",
                )
                WarlockOutlinedButton(
                    onClick = { onEditFont(settings?.monoFont, true) { onSaveMonoFont(it) } },
                    text = "Monospace font: ${settings?.monoFont.fontLabel()}",
                )
                if (nameFilterAvailable) {
                    WarlockCheckboxRow(
                        checked = settings?.nameFilter == true,
                        onCheckedChange = onSaveNameFilter,
                        text = "Only show lines with names in list",
                    )
                }
                WarlockButton(onClick = onRevert, text = "Revert to defaults")
            }
        }
    }
}

private fun windowRowLabel(
    name: String,
    title: String?,
): String = if (title != null && title.isNotBlank() && title != name) "$name ($title)" else name
