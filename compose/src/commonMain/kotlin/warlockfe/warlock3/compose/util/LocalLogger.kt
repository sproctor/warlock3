package warlockfe.warlock3.compose.util

import androidx.compose.runtime.staticCompositionLocalOf
import co.touchlab.kermit.Logger

val LocalLogger = staticCompositionLocalOf<Logger> {
    error("No Logger provided.")
}
