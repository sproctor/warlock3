package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StormfrontPropertyEvent

class RightHandler : BaseElementListener() {
    override fun characters(data: String) = StormfrontPropertyEvent("right", data)
}