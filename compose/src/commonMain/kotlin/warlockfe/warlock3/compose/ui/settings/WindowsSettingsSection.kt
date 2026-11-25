package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.FontPickerDialog
import warlockfe.warlock3.compose.components.MAX_PANEL_SCALE
import warlockfe.warlock3.compose.components.MIN_PANEL_SCALE
import warlockfe.warlock3.compose.components.PANEL_SCALE_STEP
import warlockfe.warlock3.compose.components.TextStyleEditor
import warlockfe.warlock3.compose.components.fontLabel
import warlockfe.warlock3.compose.components.formatPanelScale
import warlockfe.warlock3.compose.components.toFontConfig
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.arrow_right
import warlockfe.warlock3.compose.shim.WarlockButton
import warlockfe.warlock3.compose.shim.WarlockCheckboxRow
import warlockfe.warlock3.compose.shim.WarlockOutlinedButton
import warlockfe.warlock3.compose.shim.WarlockText
import warlockfe.warlock3.compose.shim.WarlockTextField
import warlockfe.warlock3.compose.shim.disclosureArrowSize
import warlockfe.warlock3.compose.shim.warlockContentColor
import warlockfe.warlock3.compose.ui.window.toStyleLayer
import warlockfe.warlock3.compose.util.LocalDarkTheme
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.toColorPalette
import warlockfe.warlock3.core.prefs.models.WindowSettings
import warlockfe.warlock3.core.text.FontConfig
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyleLayer
import warlockfe.warlock3.core.text.StyleScope
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.text.resolveRefs
import warlockfe.warlock3.core.text.resolveSourced
import warlockfe.warlock3.core.text.sampleStyle
import warlockfe.warlock3.core.text.toLayer
import warlockfe.warlock3.core.window.WindowInfo
import warlockfe.warlock3.core.window.WindowType
import kotlin.math.roundToInt

/**
 * Per-window color/font settings for the selected character, grouped into shown windows, windows with
 * saved settings that aren't shown, a collapsed list of other (server-known but unconfigured) windows,
 * and a field to add settings for a window by id. Styling is written straight to the repository through
 * [WindowsViewModel]; show/hide routes through [liveContext] when the character is connected so the
 * running layout updates, otherwise through the repository (applied on next connect).
 */
@Composable
fun WindowsSettingsSection(
    characterId: String,
    viewModel: WindowsViewModel,
    // The edited character's panel scale, which a window's override falls back to. Passed in
    // rather than read from LocalPanelDefaults: that local carries the *connected* character's
    // scale, and these settings can be edited for a different character, or with no game up.
    characterPanelScale: Float,
    defaultStyle: StyleDefinition,
    liveContext: WindowSettingsLiveContext?,
    initialWindowTarget: String?,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSkin.current.toColorPalette(LocalDarkTheme.current)
    val windowSettings by viewModel.windowSettings.collectAsState()

    val connected = liveContext?.takeIf { it.connectedCharacterId == characterId }
    val emptyWindowInfo = remember { MutableStateFlow(emptyList<WindowInfo>()) }
    val liveWindows by (connected?.windowInfo ?: emptyWindowInfo).collectAsState()
    val titlesByName = remember(liveWindows) { liveWindows.associateBy { it.name } }

    val settingsByName = remember(windowSettings) { windowSettings.associateBy { it.name } }
    val savedNames = settingsByName.keys
    val announcedNames = titlesByName.keys
    // "main" is always shown and can't be hidden, so it's handled separately (it has no position row).
    val openWindows =
        remember(windowSettings) { windowSettings.filter { it.open && it.name != MAIN_WINDOW } }
    val savedNotOpen =
        remember(windowSettings) { windowSettings.filter { !it.open && it.name != MAIN_WINDOW } }
    val hiddenWindows =
        remember(liveWindows, settingsByName) {
            liveWindows.filter { it.name !in settingsByName && it.name != MAIN_WINDOW }.sortedBy { it.name }
        }
    val hiddenNames = remember(hiddenWindows) { hiddenWindows.map { it.name }.toSet() }
    val openNames = remember(openWindows) { openWindows.map { it.name }.toSet() }

    // Rows expanded to their editor, seeded from the window a "Window settings" action targeted.
    val expanded = remember { mutableStateListOf<String>() }
    // Names typed into the add field for windows we know nothing about yet; they render an empty editor
    // and become real rows once a control is changed (which writes a config entry).
    val pending = remember { mutableStateListOf<String>() }
    var otherExpanded by remember { mutableStateOf(false) }
    // Windows the user asked to see whose group isn't known yet; see settleRevealRequests.
    val revealRequests = remember { mutableStateListOf<String>() }

    fun requestWindow(name: String) {
        if (name !in expanded) expanded.add(name)
        if (needsPendingRow(name, savedNames, announcedNames) && name !in pending) pending.add(name)
        if (name !in revealRequests) revealRequests.add(name)
    }
    LaunchedEffect(initialWindowTarget) {
        initialWindowTarget?.let { requestWindow(it) }
    }
    LaunchedEffect(hiddenNames, savedNames, announcedNames) {
        snapshotFlow { revealRequests.toList() }.collect { requests ->
            val outcome = settleRevealRequests(requests, hiddenNames, savedNames, announcedNames)
            if (outcome.openOtherWindows) otherExpanded = true
            if (outcome.unsettled.size != requests.size) revealRequests.retainAll(outcome.unsettled.toSet())
        }
    }

    // One font picker is open at a time across every row (each row's shared style editor owns its own
    // color picker).
    var editFont by remember { mutableStateOf<Triple<FontConfig?, Boolean, (FontConfig?) -> Unit>?>(null) }
    editFont?.let { (current, monoOnly, onSave) ->
        FontPickerDialog(
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
        // Keyed so a row's remembered state (an open color picker, say) stays with its window when the
        // groups reorder.
        key(name) {
            WindowRow(
                name = name,
                title = titlesByName[name]?.title,
                settings = settings,
                defaultStyle = defaultStyle,
                palette = palette,
                isOpen = name in openNames,
                canToggleShown = name != MAIN_WINDOW,
                nameFilterAvailable = titlesByName[name]?.nameFilterOption == true || settings?.nameFilter == true,
                // Only panels lay widgets out in game-supplied pixels. Offline we have no window info, so
                // keep the row for anyone who already set a scale rather than stranding their override.
                scaleAvailable = titlesByName[name]?.windowType == WindowType.PANEL || settings?.scale != null,
                characterPanelScale = characterPanelScale,
                expanded = name in expanded,
                onToggleExpand = { if (name in expanded) expanded.remove(name) else expanded.add(name) },
                onSetVisible = { show ->
                    if (connected != null) {
                        if (show) connected.openWindow(name) else connected.closeWindow(name)
                    } else {
                        viewModel.setWindowShown(name, show)
                    }
                },
                onSaveStyle = { layer -> viewModel.saveStyle(name, layer) },
                onSaveFont = { font -> viewModel.saveFont(name, font) },
                onSaveMonoFont = { font -> viewModel.saveMonoFont(name, font) },
                onSaveNameFilter = { value -> viewModel.saveNameFilter(name, value) },
                onSaveScale = { value -> viewModel.saveScale(name, value) },
                onRevert = { viewModel.revert(name) },
                onEditFont = { current, monoOnly, onSave -> editFont = Triple(current, monoOnly, onSave) },
            )
        }
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val pendingRows = pending.filter { needsPendingRow(it, savedNames, announcedNames) }

        GroupLabel("Shown")
        row(MAIN_WINDOW, settingsByName[MAIN_WINDOW])
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
            fun add() {
                val name = addState.text.toString().trim()
                if (name.isNotEmpty()) {
                    requestWindow(name)
                    addState.clearText()
                }
            }
            WarlockTextField(
                state = addState,
                modifier = Modifier.weight(1f),
                onSubmit = { add() },
                placeholder = "Window id",
            )
            WarlockOutlinedButton(onClick = { add() }, text = "Add")
        }
    }
}

@Composable
private fun GroupLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    WarlockText(
        text = text,
        modifier = modifier.padding(top = 8.dp),
        color = warlockContentColor.copy(alpha = 0.7f),
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
            .expandableRow(expanded, onClick)
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DisclosureArrow(expanded = expanded, tint = warlockContentColor.copy(alpha = 0.7f))
        Spacer(Modifier.width(6.dp))
        WarlockText(label, color = warlockContentColor.copy(alpha = 0.7f))
    }
}

@Composable
private fun WindowRow(
    name: String,
    title: String?,
    settings: WindowSettings?,
    defaultStyle: StyleDefinition,
    palette: Map<String, WarlockColor>,
    isOpen: Boolean,
    canToggleShown: Boolean,
    nameFilterAvailable: Boolean,
    scaleAvailable: Boolean,
    characterPanelScale: Float,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onSetVisible: (Boolean) -> Unit,
    onSaveStyle: (StyleLayer) -> Unit,
    onSaveFont: (FontConfig?) -> Unit,
    onSaveMonoFont: (FontConfig?) -> Unit,
    onSaveNameFilter: (Boolean) -> Unit,
    onSaveScale: (Float?) -> Unit,
    onRevert: () -> Unit,
    onEditFont: (FontConfig?, Boolean, (FontConfig?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().expandableRow(expanded, onToggleExpand).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DisclosureArrow(expanded = expanded, tint = warlockContentColor)
            Spacer(Modifier.width(ROW_ARROW_GAP))
            WarlockText(windowRowLabel(name, title), modifier = Modifier.weight(1f))
            if (canToggleShown) {
                WarlockOutlinedButton(onClick = { onSetVisible(!isOpen) }, text = if (isOpen) "Hide" else "Show")
            }
        }
        if (expanded) {
            Column(
                Modifier.fillMaxWidth().padding(start = disclosureArrowSize + ROW_ARROW_GAP, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // A window has no cascade of its own; the character's default text style is the fallback.
                // Feed it to the sample (for a realistic preview) but not to the source tags, so unset
                // attributes read "default" rather than "from skin".
                val windowLayer = (settings?.toStyleLayer() ?: StyleLayer()).resolveRefs(palette)
                TextStyleEditor(
                    sourced = resolveSourced(listOf(StyleScope.CHARACTER to windowLayer)),
                    sample =
                        sampleStyle(
                            listOf(StyleScope.CHARACTER to windowLayer, StyleScope.SKIN to defaultStyle.toLayer()),
                        ),
                    editScope = StyleScope.CHARACTER,
                    editLayer = windowLayer,
                    onSave = { onSaveStyle(it) },
                    showFont = false,
                    palette = palette,
                )
                WarlockOutlinedButton(
                    onClick = { onEditFont(settings?.font, false) { onSaveFont(it) } },
                    text = "Font: ${settings?.font.fontLabel()}",
                )
                WarlockOutlinedButton(
                    onClick = { onEditFont(settings?.monoFont, true) { onSaveMonoFont(it) } },
                    text = "Monospace font: ${settings?.monoFont.fontLabel()}",
                )
                if (scaleAvailable) {
                    WindowScaleRow(
                        scale = settings?.scale,
                        characterScale = characterPanelScale,
                        onSave = onSaveScale,
                    )
                }
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

// The gap between a window row's arrow and its label; the expanded editor is indented to the label.
private val ROW_ARROW_GAP = 8.dp

/** Makes a collapsible row's header toggle it, telling accessibility services that it's expandable. */
private fun Modifier.expandableRow(
    expanded: Boolean,
    onToggle: () -> Unit,
): Modifier =
    semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
        .clickable(onClickLabel = if (expanded) "Collapse" else "Expand", role = Role.Button, onClick = onToggle)

/** The arrow of a collapsible row: pointing right when collapsed, down when [expanded]. */
@Composable
private fun DisclosureArrow(
    expanded: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(Res.drawable.arrow_right),
        contentDescription = null,
        modifier = modifier.size(disclosureArrowSize).rotate(if (expanded) 90f else 0f),
        colorFilter = ColorFilter.tint(tint),
    )
}

private fun windowRowLabel(
    name: String,
    title: String?,
): String = if (title != null && title.isNotBlank() && title != name) "$name ($title)" else name

/**
 * A panel window's scale override; null follows the character's panel scale. "Use default" wraps
 * below the stepper when the row is too narrow for both, as on a phone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WindowScaleRow(
    scale: Float?,
    characterScale: Float,
    onSave: (Float?) -> Unit,
) {
    val current = scale ?: characterScale

    fun step(delta: Float) {
        val next = ((current + delta) * 10f).roundToInt() / 10f
        onSave(next.coerceIn(MIN_PANEL_SCALE, MAX_PANEL_SCALE))
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WarlockText("Panel scale")
            WarlockOutlinedButton(onClick = { step(-PANEL_SCALE_STEP) }, text = "-")
            WarlockText(if (scale == null) "Default" else formatPanelScale(current))
            WarlockOutlinedButton(onClick = { step(PANEL_SCALE_STEP) }, text = "+")
        }
        if (scale != null) {
            WarlockOutlinedButton(onClick = { onSave(null) }, text = "Use default")
        }
    }
}
