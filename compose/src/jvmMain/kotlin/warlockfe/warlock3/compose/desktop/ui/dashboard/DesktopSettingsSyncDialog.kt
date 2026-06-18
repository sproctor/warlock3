package warlockfe.warlock3.compose.desktop.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockScrollableColumn
import warlockfe.warlock3.core.mudmobile.ConflictResolution
import warlockfe.warlock3.core.mudmobile.SyncConflict

/**
 * Resolution dialog for settings-sync conflicts: one file at a time (the first in the list), showing
 * the local and remote versions side by side so the user can pick which one wins. Resolving a file
 * removes it from the list; the dialog closes when none remain.
 */
@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun DesktopSettingsSyncConflictDialog(
    conflicts: List<SyncConflict>,
    onResolve: (path: String, resolution: ConflictResolution) -> Unit,
    onDismiss: () -> Unit,
) {
    val conflict = conflicts.firstOrNull() ?: return
    WarlockDialog(
        title = "Resolve settings conflict",
        onCloseRequest = onDismiss,
        width = 760.dp,
        height = 560.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(conflict.path, fontWeight = FontWeight.SemiBold)
            Text(
                "This file changed on this computer and on MUD Mobile. Choose which version to keep" +
                    (if (conflicts.size > 1) " (${conflicts.size} files need review)." else "."),
            )

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DiffPane(
                    title = "This computer",
                    content = conflict.localContent,
                    deletedText = "Deleted on this computer",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                DiffPane(
                    title = "MUD Mobile" + (conflict.remoteModified?.let { " (updated $it)" } ?: ""),
                    content = conflict.remoteContent,
                    deletedText = "Deleted on MUD Mobile",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WarlockButton(
                    onClick = { onResolve(conflict.path, ConflictResolution.KEEP_LOCAL) },
                    text = "Keep this computer's",
                )
                WarlockButton(
                    onClick = { onResolve(conflict.path, ConflictResolution.TAKE_REMOTE) },
                    text = "Use MUD Mobile's",
                )
                Box(Modifier.weight(1f))
                WarlockButton(onClick = onDismiss, text = "Later")
            }
        }
    }
}

@Composable
private fun DiffPane(
    title: String,
    content: String?,
    deletedText: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(2.dp)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, fontWeight = FontWeight.Medium)
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(JewelTheme.globalColors.panelBackground, shape)
                    .border(Dp.Hairline, JewelTheme.globalColors.borders.normal, shape)
                    .padding(8.dp),
        ) {
            if (content == null) {
                Text(deletedText)
            } else {
                WarlockScrollableColumn(Modifier.fillMaxSize()) {
                    Text(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        text = content,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}
