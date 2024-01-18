package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StormfrontPropertyEvent

class LeftHandler : BaseElementListener() {
    override fun characters(data: String) = StormfrontPropertyEvent("left", data)
}