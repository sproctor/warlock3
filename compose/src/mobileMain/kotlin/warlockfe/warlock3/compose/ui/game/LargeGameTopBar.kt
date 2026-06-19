package warlockfe.warlock3.compose.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.menu
import warlockfe.warlock3.compose.generated.resources.more_vert
import warlockfe.warlock3.compose.generated.resources.settings_filled

/**
 * The Large-layout top app bar, modelled on the Material 3 desktop pane in the design: a menu button
 * that toggles the window list, the character name + game/room subtitle, a connection status pill,
 * a settings button, and an overflow menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeGameTopBar(
    title: String,
    subtitle: String?,
    connected: Boolean,
    onMenu: () -> Unit,
    onSettings: () -> Unit,
    onDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onMenu) {
                Icon(painter = painterResource(Res.drawable.menu), contentDescription = "Toggle window list")
            }
        },
        title = {
            Column {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        actions = {
            ConnectionPill(connected = connected)
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onSettings) {
                Icon(painter = painterResource(Res.drawable.settings_filled), contentDescription = "Settings")
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(painter = painterResource(Res.drawable.more_vert), contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Go to dashboard") },
                        onClick = {
                            menuOpen = false
                            onDashboard()
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun ConnectionPill(connected: Boolean) {
    val container =
        if (connected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
    val content =
        if (connected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
    val dot = if (connected) Color(0xFF86D6A0) else MaterialTheme.colorScheme.error
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(container)
                .height(32.dp)
                .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(
            text = if (connected) "Connected" else "Disconnected",
            color = content,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
