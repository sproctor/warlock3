package warlockfe.warlock3.stormfront.util

data class StormfrontStreamWindow(
    val id: String,
    val title: String,
    val subtitle: String?,
    val ifClosed: String?,
    val styleIfClosed: String?,
)