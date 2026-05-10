package warlockfe.warlock3.compose.desktop.shim

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.ui.component.GroupHeader

@Composable
fun WarlockGroupHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    GroupHeader(
        text = text,
        modifier = modifier,
    )
}
