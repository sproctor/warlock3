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
        /**
         * The option the server's [value] names. It names the option's [Option.text], not its
         * [Option.value]: a box sent as `value='neck'` alongside `content_text='random,head,neck'`
         * and `content_value='rnd,hd,nk'` shows "neck" and substitutes "nk". Matching the wrong
         * column finds nothing, which is what made the real client show a literal "(ERROR)".
         */
        val serverOption: Option?
            get() = options.firstOrNull { it.text == value }

        /**
         * The option to show, given [current] - the value held for this widget in the panel's value
         * map, which is an [Option.value] because that is what `%<id>%` expands to. Falls back to
         * the server's selection when the map has nothing for this widget yet. Null means nothing is
         * selected, which is a state the panel has to be able to show: see [applyServerSelection].
         */
        fun optionFor(current: String?): Option? = options.firstOrNull { it.value == current } ?: serverOption

        /**
         * Folds a server update into a panel's [values] map, which holds [Option.value]s because
         * that is what `%<id>%` expands to.
         *
         * A [value] naming no option is the server's error state rather than a selection - the real
         * client renders a literal "(ERROR)" for it - so any entry left over from an earlier update
         * or a local pick goes, leaving the box showing nothing. A box that arrives with no [value]
         * at all says nothing about the selection, so a local one survives it.
         */
        fun applyServerSelection(values: MutableMap<String, String>) {
            val option = serverOption
            when {
                option != null -> values[id] = option.value
                value != null -> values.remove(id)
            }
        }

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
