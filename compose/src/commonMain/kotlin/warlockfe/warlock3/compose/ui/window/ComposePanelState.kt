package warlockfe.warlock3.compose.ui.window

import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import warlockfe.warlock3.core.client.PanelObject
import warlockfe.warlock3.core.window.PanelState

class ComposePanelState(
    override val id: String,
) : PanelState {
    private val cachedList = mutableListOf<PanelObject>()
    private val _objects = MutableStateFlow(emptyList<PanelObject>())
    val objects = _objects.asStateFlow()

    /**
     * Replaces the widget with this id where it already sits, and appends it only when it is new.
     *
     * The server updates a panel by resending just the widgets that changed, so this runs constantly
     * during play. Moving an updated widget to the end instead would reorder the panel under the
     * user: the layout reads flow position and draw order from this list, and the UI identifies a
     * widget by its slot in it, so every widget after the updated one would be handed the state of
     * its neighbour - which is what reset the first combat maneuver when the second was set.
     */
    override suspend fun setObject(value: PanelObject) {
        val index = cachedList.indexOfFirst { it.id == value.id }
        if (index >= 0) {
            cachedList[index] = value
        } else {
            cachedList.add(value)
        }
    }

    override suspend fun clear() {
        cachedList.clear()
    }

    override suspend fun updateState() {
        _objects.value = cachedList.toPersistentList()
    }
}
