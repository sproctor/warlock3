package warlockfe.warlock3.compose.desktop.shim

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme

@Composable
fun WarlockListItem(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    headline: @Composable () -> Unit,
) {
    val background = if (selected) JewelTheme.globalColors.borders.normal else Color.Transparent
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(background)
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (leading != null) {
                leading()
            }
            Box(modifier = Modifier.weight(1f)) {
                headline()
            }
            if (trailing != null) {
                trailing()
            }
        }
    }
}
