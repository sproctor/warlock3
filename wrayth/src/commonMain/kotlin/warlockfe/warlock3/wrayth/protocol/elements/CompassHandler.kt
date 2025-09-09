package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.WraythCompassEndEvent

class CompassHandler : BaseElementListener() {
    override fun endElement() = WraythCompassEndEvent
}