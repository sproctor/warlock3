package warlockfe.warlock3.stormfront.protocol

import warlockfe.warlock3.core.client.Percentage
import warlockfe.warlock3.core.compass.DirectionType
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.stormfront.stream.StormfrontWindow
import warlockfe.warlock3.stormfront.util.CmdDefinition
import warlockfe.warlock3.stormfront.util.StormfrontCmd

sealed interface StormfrontEvent

data class StormfrontEolEvent(val ignoreWhenBlank: Boolean) : StormfrontEvent
data class StormfrontDataReceivedEvent(val text: String) : StormfrontEvent
data class StormfrontStreamEvent(val id: String?) : StormfrontEvent
data class StormfrontClearStreamEvent(val id: String) : StormfrontEvent
data class StormfrontModeEvent(val id: String?) : StormfrontEvent
data class StormfrontAppEvent(val character: String?, val game: String?) : StormfrontEvent
data class StormfrontOutputEvent(val style: WarlockStyle?) : StormfrontEvent
data class StormfrontStyleEvent(val style: WarlockStyle?) : StormfrontEvent
data class StormfrontPushStyleEvent(val style: WarlockStyle) : StormfrontEvent
data object StormfrontPopStyleEvent : StormfrontEvent
data class StormfrontPromptEvent(val text: String) : StormfrontEvent
data class StormfrontTimeEvent(val time: String) : StormfrontEvent
data class StormfrontRoundTimeEvent(val time: String) : StormfrontEvent
data class StormfrontCastTimeEvent(val time: String) : StormfrontEvent
data class StormfrontSettingsInfoEvent(val crc: String?, val instance: String?) : StormfrontEvent
data class StormfrontProgressBarEvent(
    val id: String,
    val value: Percentage,
    val text: String,
    val left: Percentage,
    val width: Percentage
) : StormfrontEvent
data class StormfrontDialogDataEvent(val id: String?) : StormfrontEvent
data object StormfrontCompassEndEvent : StormfrontEvent
data class StormfrontDirectionEvent(val direction: DirectionType) : StormfrontEvent
data class StormfrontPropertyEvent(val key: String, val value: String?) : StormfrontEvent
data class StormfrontComponentStartEvent(val id: String) : StormfrontEvent
data object StormfrontComponentEndEvent : StormfrontEvent
data class StormfrontComponentDefinitionEvent(val id: String) : StormfrontEvent
data object StormfrontHandledEvent : StormfrontEvent
data class StormfrontStreamWindowEvent(val window: StormfrontWindow) : StormfrontEvent
data object StormfrontNavEvent : StormfrontEvent
data class StormfrontActionEvent(val text: String, val command: String) : StormfrontEvent
data class StormfrontOpenUrlEvent(val url: String) : StormfrontEvent
data class StormfrontParseErrorEvent(val text: String) : StormfrontEvent
data class StormfrontUnhandledTagEvent(val tag: String) : StormfrontEvent
data object StormfrontUpdateVerbsEvent : StormfrontEvent
data object StormfrontStartCmdList : StormfrontEvent
data object StormfrontEndCmdList : StormfrontEvent
data class StormfrontCliEvent(val cmd: CmdDefinition) : StormfrontEvent
data class StormfrontPushCmdEvent(val cmd: StormfrontCmd) : StormfrontEvent
data class StormfrontMenuStartEvent(val id: Int?) : StormfrontEvent
data object StormfrontMenuEndEvent : StormfrontEvent
data class StormfrontMenuItemEvent(val coord: String) : StormfrontEvent
