package warlockfe.warlock3.compose

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Navigation 3 destination keys for the mobile app shell. Kept serializable so the back stack can
// be saved across process death.

@Serializable
data object DashboardKey : NavKey

@Serializable
data object WizardKey : NavKey

@Serializable
data object GameKey : NavKey

@Serializable
data class ErrorKey(
    val message: String,
) : NavKey
