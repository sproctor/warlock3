package warlockfe.warlock3.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import dev.nucleusframework.core.runtime.AppImageIntegration
import dev.nucleusframework.core.runtime.AppImageIntegrationResult
import dev.nucleusframework.core.runtime.AppImageIntegrationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository

/**
 * When running as an AppImage, offers to install its menu entry and icon: GNOME resolves an app's
 * icon only through installed .desktop entries (it ignores the icon a window sets on itself), so
 * without this the dock shows a generic icon. Offered once; a stale entry (the image moved or was
 * updated) is already broken, so it is refreshed without asking again. Outside an AppImage this
 * does nothing.
 */
@Composable
internal fun AppImageIntegrationPrompt(
    clientSettings: ClientSettingRepository,
    logger: Logger,
) {
    val appImageIntegration = remember { AppImageIntegration() }
    var showIntegrationDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            when (appImageIntegration.status()) {
                AppImageIntegrationStatus.NotIntegrated -> {
                    if (!clientSettings.getIgnoreAppImageIntegration()) {
                        showIntegrationDialog = true
                    }
                }

                is AppImageIntegrationStatus.Stale -> {
                    val result = appImageIntegration.integrate()
                    if (result is AppImageIntegrationResult.Failed) {
                        logger.e(result.cause) { "AppImage integration refresh failed: ${result.reason}" }
                    }
                }

                else -> {}
            }
        }
    }
    if (showIntegrationDialog) {
        AppImageIntegrationDialog(
            integration = appImageIntegration,
            clientSettings = clientSettings,
            logger = logger,
            onDismiss = { showIntegrationDialog = false },
        )
    }
}

@Composable
private fun AppImageIntegrationDialog(
    integration: AppImageIntegration,
    clientSettings: ClientSettingRepository,
    logger: Logger,
    onDismiss: () -> Unit,
) {
    var error: String? by remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope()

    WarlockDialog(
        onCloseRequest = onDismiss,
        title = "Add Warlock to your applications?",
        width = 440.dp,
        height = 280.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "Warlock is running from an AppImage, which the desktop cannot show in menus " +
                    "or the dock with the right icon until a launcher entry is installed.",
            )
            Spacer(Modifier.padding(top = 8.dp))
            Text(
                "Add a menu entry and icon for this AppImage? If the file moves or is " +
                    "updated later, the entry is repaired automatically.",
            )
            error?.let {
                Spacer(Modifier.padding(top = 8.dp))
                Text("Adding the entry failed: $it")
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                WarlockOutlinedButton(
                    onClick = {
                        scope.launch {
                            clientSettings.putIgnoreAppImageIntegration(true)
                            onDismiss()
                        }
                    },
                    text = "Don't ask again",
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                WarlockOutlinedButton(
                    onClick = onDismiss,
                    text = "Not now",
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                WarlockButton(
                    onClick = {
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { integration.integrate() }
                            when (result) {
                                is AppImageIntegrationResult.Integrated -> {
                                    onDismiss()
                                }

                                is AppImageIntegrationResult.Failed -> {
                                    logger.e(result.cause) { "AppImage integration failed: ${result.reason}" }
                                    error = result.reason
                                }
                            }
                        }
                    },
                    text = "Add",
                )
            }
        }
    }
}
