package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythPulseEvent

// The game only sends mana='0|1'; min and max are optional, and default to the usual 46-75 seconds
// between pulses, as Saga has them.
class PulseHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent =
        WraythPulseEvent(
            manaNext = element.attributes["mana"] == "1",
            minSeconds = element.attributes["min"]?.toIntOrNull()?.takeIf { it > 0 } ?: 46,
            maxSeconds = element.attributes["max"]?.toIntOrNull()?.takeIf { it > 0 } ?: 75,
        )
}
