package warlockfe.warlock3.wrayth.util

data class WraythStreamWindow(
    val id: String,
    val title: String,
    val subtitle: String?,
    val ifClosed: String?,
    val styleIfClosed: String?,
    val timestamp: Boolean,
)