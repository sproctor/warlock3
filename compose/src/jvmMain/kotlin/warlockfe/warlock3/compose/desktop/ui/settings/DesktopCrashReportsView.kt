package warlockfe.warlock3.compose.desktop.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.crash.CrashReport
import warlockfe.warlock3.compose.crash.CrashReporting
import warlockfe.warlock3.compose.crash.CrashReportingText
import warlockfe.warlock3.compose.crash.displayTime
import warlockfe.warlock3.compose.desktop.shim.WarlockAlertDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockListItem
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton

/** The crash-reporting choice, and the reports that were sent, each viewable as sent. */
@Composable
fun DesktopCrashReportsView(
    crashReporting: CrashReporting,
    modifier: Modifier = Modifier,
) {
    val consent by crashReporting.consent.collectAsState()
    val reports by crashReporting.reports.collectAsState()
    var selected by remember { mutableStateOf<CrashReport?>(null) }
    var selectedText by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { crashReporting.refreshReports() }
    }
    LaunchedEffect(selected) {
        val report = selected
        selectedText = if (report == null) "" else withContext(Dispatchers.IO) { crashReporting.readReport(report) }
    }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(CrashReportingText.SETTINGS_DESCRIPTION)
        CheckboxRow(
            checked = consent == true,
            onCheckedChange = { crashReporting.setConsent(it) },
            text = CrashReportingText.SETTING_LABEL,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sent reports", modifier = Modifier.weight(1f))
            WarlockOutlinedButton(
                onClick = { confirmDelete = true },
                text = CrashReportingText.DELETE_ALL,
                enabled = reports.isNotEmpty(),
            )
        }
        if (reports.isEmpty()) {
            Text(CrashReportingText.NO_REPORTS)
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LazyColumn(
                    modifier =
                        Modifier
                            .width(260.dp)
                            .fillMaxHeight()
                            .bordered(),
                ) {
                    items(reports, key = { it.path.toString() }) { report ->
                        WarlockListItem(
                            selected = selected == report,
                            onClick = { selected = report },
                        ) {
                            Column {
                                Text(report.displayTime())
                                Text(report.summary, maxLines = 2, color = JewelTheme.globalColors.text.info)
                            }
                        }
                    }
                }
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .bordered()
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    if (selected == null) {
                        Text("Select a report to see exactly what was sent.")
                    } else {
                        SelectionContainer {
                            Text(selectedText, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        WarlockAlertDialog(
            title = "Delete crash reports",
            text = CrashReportingText.DELETE_NOTE,
            onDismissRequest = { confirmDelete = false },
            confirmButton = {
                WarlockButton(
                    onClick = {
                        confirmDelete = false
                        selected = null
                        crashReporting.deleteReports()
                    },
                    text = "Delete",
                )
            },
            dismissButton = {
                WarlockOutlinedButton(onClick = { confirmDelete = false }, text = "Cancel")
            },
        )
    }
}

/**
 * Asks, once, whether to send crash reports. Closing the window without choosing leaves the
 * question open, so it is asked again next launch.
 */
@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun DesktopCrashReportingNotice(
    crashReporting: CrashReporting,
    onDismiss: () -> Unit,
) {
    WarlockAlertDialog(
        title = CrashReportingText.NOTICE_TITLE,
        text = CrashReportingText.NOTICE_BODY,
        onDismissRequest = onDismiss,
        confirmButton = {
            WarlockButton(
                onClick = {
                    crashReporting.setConsent(true)
                    onDismiss()
                },
                text = CrashReportingText.ACCEPT,
            )
        },
        dismissButton = {
            WarlockOutlinedButton(
                onClick = {
                    crashReporting.setConsent(false)
                    onDismiss()
                },
                text = CrashReportingText.DECLINE,
            )
        },
        width = 520.dp,
        height = 260.dp,
    )
}

@Composable
private fun Modifier.bordered(): Modifier = border(Dp.Hairline, JewelTheme.globalColors.borders.normal, RoundedCornerShape(6.dp))
