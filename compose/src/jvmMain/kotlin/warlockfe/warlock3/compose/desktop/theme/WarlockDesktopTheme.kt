package warlockfe.warlock3.compose.desktop.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.ui.ComponentStyling

@Composable
fun WarlockDesktopTheme(
    isDark: Boolean,
    content: @Composable () -> Unit,
) {
    IntUiTheme(
        theme =
            if (isDark) {
                JewelTheme.darkThemeDefinition()
            } else {
                JewelTheme.lightThemeDefinition()
            },
        styling = ComponentStyling.default()
    ) {
        CompositionLocalProvider(
            LocalWarlockColors provides WarlockColors.from(isDark),
            content = content,
        )
    }
}
