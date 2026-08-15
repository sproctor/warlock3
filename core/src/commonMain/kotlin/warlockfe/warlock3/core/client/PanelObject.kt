package warlockfe.warlock3.core.client

sealed class PanelObject {
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
    ) : PanelObject()

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
    ) : PanelObject()

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
        val justify: PanelJustify,
    ) : PanelObject()

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
    ) : PanelObject()

    /**
     * cmdButton, and closeButton when [closesPanel] is set. A close button is a command button that
     * also dismisses the panel it sits in: the real client sends the command and then closes the
     * panel (refusing, with an error of its own, when the panel is resident).
     */
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
        val closesPanel: Boolean = false,
    ) : PanelObject()

    /**
     * checkBox: a toggle that sends nothing of its own. Its state is read by another widget, whose
     * `cmd` names it as `%<id>%` and receives [checkedValue] or [uncheckedValue].
     *
     * Both values are required rather than nullable because the real client draws no checkbox at
     * all unless the wire carried both `checked_value` and `unchecked_value` - a checkbox without
     * them has nothing to contribute to a command, and Wrayth gives it neither pixels nor width.
     */
    data class CheckBox(
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
        val checked: Boolean,
        val checkedValue: String,
        val uncheckedValue: String,
    ) : PanelObject()

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
    ) : PanelObject() {
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
    ) : PanelObject()

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
    ) : PanelObject()

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
    ) : PanelObject()

    // menuLink: a link that opens the server-driven context menu for [exist] (the same menu an
    // `<a exist=...>` link in stream text opens), instead of running a fixed command.
    data class MenuLink(
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
        val exist: String?,
        val noun: String?,
    ) : PanelObject()

    // menuImage: an image (e.g. a demeanor face in the befriend panel) that opens the same
    // server-driven context menu as [MenuLink].
    data class MenuImage(
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
        val exist: String?,
        val noun: String?,
    ) : PanelObject()
}

/**
 * How a label's text sits inside the box its width gives it. Not to be confused with [PanelObject.align],
 * which places the box itself within the panel.
 */
enum class PanelJustify {
    Left,
    Center,
    Right,
}

sealed class DataDistance {
    data class Percent(
        val value: Percentage,
    ) : DataDistance()

    data class Pixels(
        val value: Int,
    ) : DataDistance()
}
