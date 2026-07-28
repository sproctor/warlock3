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

    override suspend fun setObject(value: PanelObject) {
        cachedList.removeAll { it.id == value.id }
        cachedList.add(value)
    }

    override suspend fun clear() {
        cachedList.clear()
    }

    override suspend fun updateState() {
        _objects.value = cachedList.toPersistentList()
    }
}
