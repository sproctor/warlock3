package warlockfe.warlock3.core.client

sealed class DialogObject() {
    abstract val id: String
    abstract val left: DataDistance?
    abstract val top: DataDistance?
    abstract val width: DataDistance?
    abstract val height: DataDistance?
    abstract val topAnchor: String?
    abstract val leftAnchor: String?
    abstract val tooltip: String?

    data class Skin(
        override val id: String,
        override val left: DataDistance?,
        override val top: DataDistance?,
        override val width: DataDistance?,
        override val height: DataDistance?,
        override val topAnchor: String?,
        override val leftAnchor: String?,
        override val tooltip: String?,
        val name: String,
        val controls: List<String>,
    ) : DialogObject()

    data class ProgressBar(
        override val id: String,
        override val left: DataDistance?,
        override val top: DataDistance?,
        override val width: DataDistance?,
        override val height: DataDistance?,
        override val topAnchor: String?,
        override val leftAnchor: String?,
        override val tooltip: String?,
        val value: Percentage,
        val text: String?,
    ) : DialogObject()

    data class Label(
        override val id: String,
        override val left: DataDistance?,
        override val top: DataDistance?,
        override val width: DataDistance?,
        override val height: DataDistance?,
        override val topAnchor: String?,
        override val leftAnchor: String?,
        override val tooltip: String?,
        val value: String?,
    ) : DialogObject()

    data class Link(
        override val id: String,
        override val left: DataDistance?,
        override val top: DataDistance?,
        override val width: DataDistance?,
        override val height: DataDistance?,
        override val topAnchor: String?,
        override val leftAnchor: String?,
        override val tooltip: String?,
        val value: String?,
        val cmd: String?,
        val echo: String?,
    ) : DialogObject()

    // cmdButton
    data class Button(
        override val id: String,
        override val left: DataDistance?,
        override val top: DataDistance?,
        override val width: DataDistance?,
        override val height: DataDistance?,
        override val topAnchor: String?,
        override val leftAnchor: String?,
        override val tooltip: String?,
        val value: String?,
        val cmd: String?,
        val echo: String?,
    ) : DialogObject()

    // dropdownbox

    // radio

    data class Image(
        override val id: String,
        override val left: DataDistance?,
        override val top: DataDistance?,
        override val width: DataDistance?,
        override val height: DataDistance?,
        override val topAnchor: String?,
        override val leftAnchor: String?,
        override val tooltip: String?,
        val name: String?,
        val cmd: String?,
        val echo: String?,
    ) : DialogObject()
}

sealed class DataDistance {
    data class Percent(val value: Percentage) : DataDistance()
    data class Pixels(val value: Int) : DataDistance()
}
