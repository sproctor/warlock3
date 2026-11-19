package warlockfe.warlock3.compose.shim

/**
 * Which of [items] a dropdown shows for [selected], or -1 for nothing: the one rule both platforms'
 * [WarlockDropdownSelect] follow.
 *
 * A null [selected] that is not itself one of the items is nothing selected (a game-sent selection
 * that names no option, say); null is a real item where T is nullable (the character selector's
 * "Global"). A non-null one that is not in the list, one since removed from it, shows the first item.
 */
internal fun <T> dropdownSelectedIndex(
    items: List<T>,
    selected: T?,
): Int {
    val index = items.indexOfFirst { it == selected }
    return if (index < 0 && selected != null && items.isNotEmpty()) 0 else index
}
