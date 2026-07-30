package warlockfe.warlock3.core.window

import warlockfe.warlock3.core.client.PanelObject

interface PanelState {
    val id: String

    suspend fun setObject(value: PanelObject)

    suspend fun clear()

    suspend fun updateState()
}
