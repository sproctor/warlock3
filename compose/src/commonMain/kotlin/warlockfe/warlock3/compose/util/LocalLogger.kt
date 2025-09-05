package warlockfe.warlock3.compose.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.oshai.kotlinlogging.KLogger

val LocalLogger = staticCompositionLocalOf<KLogger> {
    error("No Logger provided.")
}
