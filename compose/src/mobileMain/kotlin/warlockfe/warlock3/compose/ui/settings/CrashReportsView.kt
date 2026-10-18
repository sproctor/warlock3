package warlockfe.warlock3.compose.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import warlockfe.warlock3.compose.components.ScrollableColumn
import warlockfe.warlock3.compose.crash.CrashReport
import warlockfe.warlock3.compose.crash.CrashReporting
import warlockfe.warlock3.compose.crash.CrashReportingText
import warlockfe.warlock3.compose.crash.displayTime

/** The crash-reporting choice, and the saved copy of each report. */
@Composable
fun CrashReportsView(
    crashReporting: CrashReporting,
    modifier: Modifier = Modifier,
) {
    val consent by crashReporting.consent.collectAsState()
    val reports by crashReporting.reports.collectAsState()
    var selected by remember { mutableStateOf<CrashReport?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { crashReporting.refreshReports() }
    }

    ScrollableColumn(modifier.fillMaxSize()) {
        Text(CrashReportingText.SETTINGS_DESCRIPTION, modifier = Modifier.padding(16.dp))
        ListItem(
            modifier = Modifier.clickable { crashReporting.setConsent(consent != true) },
            headlineContent = { Text(CrashReportingText.SETTING_LABEL) },
            trailingContent = {
                Switch(checked = consent == true, onCheckedChange = { crashReporting.setConsent(it) })
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Saved reports",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedButton(onClick = { confirmDelete = true }, enabled = reports.isNotEmpty()) {
                Text(CrashReportingText.DELETE_ALL)
            }
        }
        if (reports.isEmpty()) {
            Text(CrashReportingText.NO_REPORTS, modifier = Modifier.padding(16.dp))
        }
        reports.forEach { report ->
            ListItem(
                modifier = Modifier.clickable { selected = report },
                headlineContent = { Text(report.displayTime()) },
                supportingContent = { Text(report.summary, maxLines = 2) },
            )
        }
    }

    selected?.let { report ->
        var text by remember(report) { mutableStateOf("") }
        LaunchedEffect(report) {
            text = withContext(Dispatchers.IO) { crashReporting.readReport(report) }
        }
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(report.displayTime()) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(
                            text = text,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            softWrap = false,
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selected = null }) { Text("Close") }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete crash reports") },
            text = { Text(CrashReportingText.DELETE_NOTE) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        crashReporting.deleteReports()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * Asks, once, whether to send crash reports. Dismissing it without choosing leaves the question
 * open, so it is asked again next launch.
 */
@Composable
fun CrashReportingNotice(
    crashReporting: CrashReporting,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(CrashReportingText.NOTICE_TITLE) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(CrashReportingText.NOTICE_BODY)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    crashReporting.setConsent(true)
                    onDismiss()
                },
            ) { Text(CrashReportingText.ACCEPT) }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    crashReporting.setConsent(false)
                    onDismiss()
                },
            ) { Text(CrashReportingText.DECLINE) }
        },
    )
}
