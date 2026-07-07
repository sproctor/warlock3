package warlockfe.warlock3.core.client

sealed class DialogObject {
    abstract val id: String
    abstract val left: DataDistance?
    abstract val top: DataDistance?
    abstract val width: DataDistance?
    abstract val height: DataDistance?
    abstract val align: String?
    abstract val topAnchor: String?
    abstract val leftAnchor: String?
    abstract val tooltip: String?

    data class Skin(
        override val id: String,
        override val left: DataDistance?,
        override val top: DataDistance?,
        override val width: DataDistance?,
        override val height: DataDistance?,
        override val align: String?,
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
        override val align: String?,
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
        override val align: String?,
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
        override val align: String?,
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
        override val align: String?,
        override val topAnchor: String?,
        override val leftAnchor: String?,
        override val tooltip: String?,
        val value: String?,
        val cmd: String?,
        val echo: String?,
    ) : DialogObject()

    // dropDownBox: a selector. Picking an option runs [cmd] with `%<id>%` replaced by the option value.
    data class DropDownBox(
        override val id: String,
        override val left: DataDistance?,
        override val top: DataDistance?,
        override val width: DataDistance?,
        override val height: DataDistance?,
        override val align: String?,
        override val topAnchor: String?,
        override val leftAnchor: String?,
        override val tooltip: String?,
        val value: String?,
        val cmd: String?,
        val options: List<Option>,
    ) : DialogObject() {
        data class Option(
            val text: String,
            val value: String,
        )
    }

    // radio: a grouped radio button. Selecting it runs [cmd].
    data class Radio(
        override val id: String,
        override val left: DataDistance?,
        override val top: DataDistance?,
        override val width: DataDistance?,
        override val height: DataDistance?,
        override val align: String?,
        override val topAnchor: String?,
        override val leftAnchor: String?,
        override val tooltip: String?,
        val text: String?,
        val cmd: String?,
        val group: String?,
        val selected: Boolean,
    ) : DialogObject()

    // upDownEditBox: a numeric spinner clamped to [min, max]. Changing it runs [cmd] with `%<id>%`.
    data class UpDownEditBox(
        override val id: String,
        override val left: DataDistance?,
        override val top: DataDistance?,
        override val width: DataDistance?,
        override val height: DataDistance?,
        override val align: String?,
        override val topAnchor: String?,
        override val leftAnchor: String?,
        override val tooltip: String?,
        val value: Int?,
        val min: Int?,
        val max: Int?,
        val cmd: String?,
    ) : DialogObject()

    data class Image(
        override val id: String,
        override val left: DataDistance?,
        override val top: DataDistance?,
        override val width: DataDistance?,
        override val height: DataDistance?,
        override val align: String?,
        override val topAnchor: String?,
        override val leftAnchor: String?,
        override val tooltip: String?,
        val name: String?,
        val cmd: String?,
        val echo: String?,
    ) : DialogObject()
}

sealed class DataDistance {
    data class Percent(
        val value: Percentage,
    ) : DataDistance()

    data class Pixels(
        val value: Int,
    ) : DataDistance()
}
