package warlockfe.warlock3.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import warlockfe.warlock3.compose.ui.theme.AppTheme
import warlockfe.warlock3.core.prefs.SettingsProblem

/**
 * What the app shows instead of itself when the user's settings could not be read: it will not
 * start with empty settings and save them over the real ones, and this says so, and where they are.
 */
@Composable
fun SettingsUnreadableScreen(
    problem: SettingsProblem,
    modifier: Modifier = Modifier,
) {
    AppTheme(useDarkTheme = isSystemInDarkTheme()) {
        Surface(modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .safeDrawingPadding()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Warlock cannot start", style = MaterialTheme.typography.headlineSmall)
                Text(problem.message)
            }
        }
    }
}
