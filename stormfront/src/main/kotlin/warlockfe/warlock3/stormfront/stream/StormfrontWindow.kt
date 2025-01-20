package warlockfe.warlock3.stormfront.stream

data class StormfrontWindow(
    val id: String,
    val title: String,
    val subtitle: String?,
    val ifClosed: String?,
    val styleIfClosed: String?,
)