package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.stormfront.protocol.BaseElementListener
import cc.warlock.warlock3.stormfront.protocol.StormfrontPropertyEvent

class LeftHandler : BaseElementListener() {
    override fun characters(data: String) = StormfrontPropertyEvent("left", data)
}