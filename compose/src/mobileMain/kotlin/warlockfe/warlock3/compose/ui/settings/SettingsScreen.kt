package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.components.LocalScrollbarGutter
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.crash.CrashReporting
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.arrow_right
import warlockfe.warlock3.compose.generated.resources.close
import warlockfe.warlock3.core.client.GameCharacter

/**
 * Top-level settings navigator. Shows a list of [SettingsPage] categories; selecting one shows that
 * page's content. The top-bar navigation icon backs out of a page to the list, then closes settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModelFactory: SettingsViewModelFactory,
    crashReporting: CrashReporting,
    currentCharacter: GameCharacter?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: SettingsPage? = null,
    initialWindowTarget: String? = null,
    windowLiveContext: WindowSettingsLiveContext? = null,
) {
    var page: SettingsPage? by remember { mutableStateOf(initialPage) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(page?.title ?: "Settings") },
                navigationIcon = {
                    IconButton(onClick = { if (page == null) onClose() else page = null }) {
                        Icon(
                            painter =
                                painterResource(
                                    if (page == null) Res.drawable.close else Res.drawable.arrow_right,
                                ),
                            contentDescription = if (page == null) "Close" else "Back",
                            modifier = if (page == null) Modifier else Modifier.rotate(180f),
                        )
                    }
                },
            )
        },
    ) { padding ->
        // Forms in settings run controls to the right edge, so keep them clear of the scrollbar.
        CompositionLocalProvider(LocalScrollbarGutter provides true) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                val selected = page
                if (selected == null) {
                    ScrollableColumn {
                        SettingsGroup.entries.forEach { group ->
                            if (group.title.isNotEmpty()) {
                                Text(
                                    text = group.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                                )
                            }
                            SettingsPage.entries.filter { it.group == group }.forEach { entry ->
                                ListItem(
                                    modifier = Modifier.clickable { page = entry },
                                    headlineContent = { Text(entry.title) },
                                    leadingContent = {
                                        Icon(
                                            painter = painterResource(entry.icon),
                                            contentDescription = null,
                                        )
                                    },
                                    trailingContent = {
                                        Icon(
                                            painter = painterResource(Res.drawable.arrow_right),
                                            contentDescription = null,
                                        )
                                    },
                                )
                            }
                        }
                    }
                } else {
                    // The page margin, as on desktop. Pages that are nothing but a Material list run edge
                    // to edge instead, like the category list above, since list rows inset themselves.
                    val margin =
                        when (selected) {
                            SettingsPage.CrashReports, SettingsPage.Accounts, SettingsPage.Characters -> 0.dp
                            else -> SettingsPageMargin
                        }
                    Box(Modifier.padding(horizontal = margin)) {
                        SettingsContent(
                            page = selected,
                            currentCharacter = currentCharacter,
                            settingsViewModelFactory = settingsViewModelFactory,
                            crashReporting = crashReporting,
                            initialWindowTarget = initialWindowTarget,
                            windowLiveContext = windowLiveContext,
                        )
                    }
                }
            }
        }
    }
}

/** The horizontal margin around a settings page's content. */
internal val SettingsPageMargin = 16.dp

/**
 * Widens a Material list row by [SettingsPageMargin] on each side, out to the screen edges. A
 * `ListItem` insets its content by 16dp itself, so inside the page margin its text would otherwise
 * start 16dp further in than the page's title and fields; bled out, the two line up.
 */
internal fun Modifier.bleedIntoPageMargin(): Modifier =
    layout { measurable, constraints ->
        if (!constraints.hasBoundedWidth) {
            val placeable = measurable.measure(constraints)
            return@layout layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        }
        val bleed = SettingsPageMargin.roundToPx()
        val placeable =
            measurable.measure(
                constraints.copy(minWidth = constraints.minWidth + 2 * bleed, maxWidth = constraints.maxWidth + 2 * bleed),
            )
        layout(placeable.width - 2 * bleed, placeable.height) { placeable.placeRelative(-bleed, 0) }
    }
