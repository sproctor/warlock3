package warlockfe.warlock3.compose.ui.window

import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import warlockfe.warlock3.core.client.DialogObject
import warlockfe.warlock3.core.window.DialogState

class ComposeDialogState(
    override val id: String,
) : DialogState {

    private val cachedList = mutableListOf<DialogObject>()
    private val _objects = MutableStateFlow(emptyList<DialogObject>())
    val objects = _objects.asStateFlow()

    override suspend fun setObject(value: DialogObject) {
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
