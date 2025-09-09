package warlockfe.warlock3.wrayth.protocol

import warlockfe.warlock3.core.client.DialogObject
import warlockfe.warlock3.core.compass.DirectionType
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.wrayth.util.WraythStreamWindow
import warlockfe.warlock3.wrayth.util.CmdDefinition
import warlockfe.warlock3.wrayth.util.WraythCmd
import warlockfe.warlock3.wrayth.util.WraythDialogWindow

sealed interface WraythEvent

data class WraythEolEvent(val ignoreWhenBlank: Boolean) : WraythEvent
data class WraythDataReceivedEvent(val text: String) : WraythEvent
data class WraythStreamEvent(val id: String?) : WraythEvent
data class WraythClearStreamEvent(val id: String) : WraythEvent
data class WraythModeEvent(val id: String?) : WraythEvent
data class WraythAppEvent(val character: String?, val game: String?) : WraythEvent
data class WraythOutputEvent(val style: WarlockStyle?) : WraythEvent
data class WraythStyleEvent(val style: WarlockStyle?) : WraythEvent
data class WraythPushStyleEvent(val style: WarlockStyle) : WraythEvent
data object WraythPopStyleEvent : WraythEvent
data class WraythPromptEvent(val text: String) : WraythEvent
data class WraythTimeEvent(val time: Long) : WraythEvent
data class WraythRoundTimeEvent(val time: String) : WraythEvent
data class WraythCastTimeEvent(val time: String) : WraythEvent
data class WraythSettingsInfoEvent(val crc: String?, val instance: String?) : WraythEvent
data class WraythDialogObjectEvent(val data: DialogObject) : WraythEvent
data class WraythDialogDataEvent(val id: String?, val clear: Boolean) : WraythEvent
data object WraythCompassEndEvent : WraythEvent
data class WraythDirectionEvent(val direction: DirectionType) : WraythEvent
data class WraythPropertyEvent(val key: String, val value: String?) : WraythEvent
data class WraythComponentStartEvent(val id: String) : WraythEvent
data object WraythComponentEndEvent : WraythEvent
data class WraythComponentDefinitionEvent(val id: String) : WraythEvent
data object WraythHandledEvent : WraythEvent
data class WraythStreamWindowEvent(val window: WraythStreamWindow) : WraythEvent
data class WraythDialogWindowEvent(val window: WraythDialogWindow) : WraythEvent
data object WraythNavEvent : WraythEvent
data class WraythActionEvent(val text: String, val command: String) : WraythEvent
data class WraythOpenUrlEvent(val url: String) : WraythEvent
data class WraythParseErrorEvent(val text: String) : WraythEvent
data class WraythUnhandledTagEvent(val tag: String) : WraythEvent
data object WraythUpdateVerbsEvent : WraythEvent
data object WraythStartCmdList : WraythEvent
data object WraythEndCmdList : WraythEvent
data class WraythCliEvent(val cmd: CmdDefinition) : WraythEvent
data class WraythPushCmdEvent(val cmd: WraythCmd) : WraythEvent
data class WraythMenuStartEvent(val id: Int?) : WraythEvent
data object WraythMenuEndEvent : WraythEvent
data class WraythMenuItemEvent(val coord: String, val noun: String?, val category: String?) : WraythEvent
data class WraythResourceEvent(val picture: String) : WraythEvent