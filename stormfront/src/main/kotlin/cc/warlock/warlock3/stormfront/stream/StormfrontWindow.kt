package cc.warlock.warlock3.stormfront.stream

data class StormfrontWindow(
    val name: String,
    val title: String,
    val subtitle: String?,
    val ifClosed: String?,
    val styleIfClosed: String?,
)