package warlockfe.warlock3.app.updater

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import dev.nucleusframework.updater.NucleusUpdater
import dev.nucleusframework.updater.UpdateInfo
import dev.nucleusframework.updater.UpdateResult
import dev.nucleusframework.updater.provider.GitHubProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.ui.component.HorizontalProgressBar
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton
import warlockfe.warlock3.core.prefs.repositories.ClientSettingRepository
import java.io.File

/** An updater for every release channel the user's setting says to check, from Warlock's GitHub releases. */
internal fun buildUpdater(
    appVersion: String,
    clientSettings: ClientSettingRepository,
): ChannelUpdater {
    val channelSetting = runBlocking { clientSettings.getReleaseChannel() }
    return ChannelUpdater(
        channelSetting.channelsToCheck(appVersion).map { channelName ->
            NucleusUpdater {
                provider = GitHubProvider(owner = "sproctor", repo = "warlock3")
                channel = channelName
                currentVersion = appVersion
            }
        },
    )
}

/**
 * Checks for an update once, when [checkOnLaunch], and shows the update dialog while [showDialog].
 * The check raises the dialog itself when it finds an update the user can install and has not said
 * to ignore.
 */
@Composable
internal fun UpdatePrompt(
    updater: ChannelUpdater,
    updateSupported: Boolean,
    checkOnLaunch: Boolean,
    clientSettings: ClientSettingRepository,
    logger: Logger,
    showDialog: Boolean,
    onShowDialogChange: (Boolean) -> Unit,
) {
    var availableUpdate: UpdateInfo? by remember { mutableStateOf(null) }
    val currentOnShowDialogChange by rememberUpdatedState(onShowDialogChange)

    if (checkOnLaunch) {
        LaunchedEffect(Unit) {
            // Only the network check runs off the main thread; the state it leads to is set here.
            when (val result = withContext(Dispatchers.IO) { updater.checkForUpdates() }) {
                is UpdateResult.Available -> {
                    availableUpdate = result.info
                    if (updateSupported && !clientSettings.getIgnoreUpdates()) {
                        currentOnShowDialogChange(true)
                    }
                }

                is UpdateResult.NotAvailable -> {
                    availableUpdate = null
                }

                is UpdateResult.Error -> {
                    logger.e(result.exception) { "Update check failed" }
                }
            }
        }
    }

    if (showDialog) {
        UpdateDialog(
            updater = updater,
            updateSupported = updateSupported,
            availableUpdate = availableUpdate,
            clientSettings = clientSettings,
            logger = logger,
            onDismiss = { onShowDialogChange(false) },
        )
    }
}

@Composable
private fun UpdateDialog(
    updater: ChannelUpdater,
    updateSupported: Boolean,
    availableUpdate: UpdateInfo?,
    clientSettings: ClientSettingRepository,
    logger: Logger,
    onDismiss: () -> Unit,
) {
    var downloadProgress: Double? by remember { mutableStateOf(null) }
    var downloadedFile: File? by remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope()

    WarlockDialog(
        onCloseRequest = onDismiss,
        title = "Warlock update available",
        width = 400.dp,
        height = 340.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            Text("Current version: ${updater.currentVersion}")
            if (updateSupported) {
                Text("Update version: ${availableUpdate?.version ?: "No update available"}")
            } else {
                Text("Automated updates are not supported for your installation")
            }
            downloadProgress?.let { percent ->
                Spacer(Modifier.padding(top = 8.dp))
                Text("Downloading: ${percent.toInt()}%")
                HorizontalProgressBar(
                    progress = (percent / 100.0).toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                val ignoreUpdates by clientSettings
                    .observeIgnoreUpdates()
                    .collectAsState(false)
                if (!ignoreUpdates) {
                    WarlockOutlinedButton(
                        onClick = {
                            scope.launch {
                                clientSettings.putIgnoreUpdates(true)
                                onDismiss()
                            }
                        },
                        text = "Ignore updates",
                    )
                } else {
                    WarlockOutlinedButton(
                        onClick = {
                            scope.launch {
                                clientSettings.putIgnoreUpdates(false)
                            }
                        },
                        text = "Stop ignoring updates",
                    )
                }
                Spacer(Modifier.padding(horizontal = 4.dp))
                WarlockOutlinedButton(
                    onClick = onDismiss,
                    text = "Close",
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                WarlockButton(
                    onClick = {
                        val info = availableUpdate ?: return@WarlockButton
                        val file = downloadedFile
                        if (file != null) {
                            updater.installAndRestart(file)
                            return@WarlockButton
                        }
                        scope.launch {
                            clientSettings.putIgnoreUpdates(false)
                            try {
                                updater.downloadUpdate(info).collect { progress ->
                                    downloadProgress = progress.percent
                                    progress.file?.let { downloadedFile = it }
                                }
                                downloadedFile?.let { updater.installAndRestart(it) }
                            } catch (e: Exception) {
                                ensureActive()
                                logger.e(e) { "Update download failed" }
                                downloadProgress = null
                            }
                        }
                    },
                    enabled = availableUpdate != null && updateSupported && downloadProgress == null,
                    text = if (downloadedFile != null) "Install & restart" else "Update",
                )
            }
        }
    }
}
