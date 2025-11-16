package warlockfe.warlock3.core.window

import warlockfe.warlock3.core.client.DialogObject

interface DialogState {
    val id: String

    suspend fun setObject(value: DialogObject)

    suspend fun clear()
}