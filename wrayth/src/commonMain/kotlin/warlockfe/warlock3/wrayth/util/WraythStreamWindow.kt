package warlockfe.warlock3.wrayth.util

data class WraythStreamWindow(
    val name: String,
    val title: String,
    val subtitle: String?,
    val ifClosed: String?,
    val styleIfClosed: String?,
    val timestamp: Boolean,
    val nameFilterOption: Boolean = false,
    val applyStyling: Boolean = true,
    // The protocol's location attribute, verbatim; where the game suggests the window docks.
    val location: String? = null,
)
