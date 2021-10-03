package cc.warlock.warlock3.stormfront.protocol

import cc.warlock.warlock3.core.Percentage
import cc.warlock.warlock3.core.WarlockStyle
import cc.warlock.warlock3.core.compass.DirectionType

sealed class StormfrontEvent
object StormfrontEolEvent : StormfrontEvent()
data class StormfrontDataReceivedEvent(val text: String) : StormfrontEvent()
data class StormfrontStreamEvent(val id: String?) : StormfrontEvent()
data class StormfrontModeEvent(val id: String?) : StormfrontEvent()
data class StormfrontAppEvent(val character: String?, val game: String?) : StormfrontEvent()
data class StormfrontOutputEvent(val style: WarlockStyle?) : StormfrontEvent()
data class StormfrontStyleEvent(val style: WarlockStyle?) : StormfrontEvent()
data class StormfrontPromptEvent(val text: String) : StormfrontEvent()
data class StormfrontTimeEvent(val time: String) : StormfrontEvent()
data class StormfrontRoundTimeEvent(val time: String) : StormfrontEvent()
data class StormfrontCastTimeEvent(val time: String) : StormfrontEvent()
data class StormfrontSettingsInfoEvent(val crc: String?, val instance: String?) : StormfrontEvent()
data class StormfrontProgressBarEvent(
    val id: String,
    val value: Percentage,
    val text: String,
    val left: Percentage,
    val width: Percentage
) : StormfrontEvent()
data class StormfrontDialogDataEvent(val id: String?) : StormfrontEvent()
object StormfrontCompassEndEvent : StormfrontEvent()
data class StormfrontDirectionEvent(val direction: DirectionType) : StormfrontEvent()
data class StormfrontPropertyEvent(val key: String, val value: String?) : StormfrontEvent()
data class StormfrontComponentStartEvent(val id: String) : StormfrontEvent()
data class StormfrontComponentTextEvent(val text: String) : StormfrontEvent()
object StormfrontComponentEndEvent : StormfrontEvent()
