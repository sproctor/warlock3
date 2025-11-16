package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.WraythRightEvent

class RightHandler : BaseElementListener() {
    override fun characters(data: String) = WraythRightEvent(data)
}