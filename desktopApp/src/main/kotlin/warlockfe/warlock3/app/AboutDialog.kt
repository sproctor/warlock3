package warlockfe.warlock3.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.nucleus.core.runtime.NucleusApp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.app.resources.Res
import warlockfe.warlock3.app.resources.icon
import warlockfe.warlock3.compose.desktop.shim.WarlockButton
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog

@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun AboutDialog(onCloseRequest: () -> Unit) {
    val warlockVersion = NucleusApp.version ?: "development"
    WarlockDialog(
        title = "About Warlock",
        onCloseRequest = onCloseRequest,
        width = 400.dp,
        height = 300.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth()) {
                Image(
                    modifier = Modifier.size(50.dp),
                    painter = painterResource(Res.drawable.icon),
                    contentDescription = "Warlock logo",
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Warlock FE")
                    Text("Version: $warlockVersion")
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    WarlockButton(onClick = onCloseRequest, text = "OK")
                }
            }
        }
    }
}
