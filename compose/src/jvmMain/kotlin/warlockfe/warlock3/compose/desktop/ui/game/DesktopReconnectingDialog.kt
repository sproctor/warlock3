package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.HorizontalProgressBar
import org.jetbrains.jewel.ui.component.Text
import warlockfe.warlock3.compose.desktop.shim.WarlockDialog
import warlockfe.warlock3.compose.desktop.shim.WarlockOutlinedButton

/**
 * Modal progress dialog shown while a game reconnect is in flight. The reconnect re-runs the full
 * connect (for hosted sessions: a fresh SGE login plus a wait for the cloud session to come up),
 * which can take many seconds, so without this the user just sees a frozen, still-disconnected game.
 *
 * The only action is "Go to dashboard", which leaves the game screen; that path closes the game
 * client and cancels the reconnect (tearing down the SGE client it opened) via GameViewModel.close.
 */
@Composable
fun DesktopReconnectingDialog(
    onGoToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // No close (X) action: the reconnect is running; the deliberate way out is "Go to dashboard".
    WarlockDialog(
        title = "Reconnecting",
        onCloseRequest = {},
        width = 420.dp,
        height = 180.dp,
    ) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Reconnecting to the game...")
            // Jewel ships only a determinate progress bar, so loop it 0 -> 100% to convey activity.
            val transition = rememberInfiniteTransition()
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
            )
            HorizontalProgressBar(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                WarlockOutlinedButton(onClick = onGoToDashboard, text = "Go to dashboard")
            }
        }
    }
}
