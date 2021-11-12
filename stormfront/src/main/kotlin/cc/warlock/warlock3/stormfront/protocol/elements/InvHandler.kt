package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.stormfront.protocol.BaseElementListener
import cc.warlock.warlock3.stormfront.protocol.StormfrontEvent
import cc.warlock.warlock3.stormfront.protocol.StormfrontHandledEvent

class InvHandler : BaseElementListener() {
    override fun characters(data: String): StormfrontEvent = StormfrontHandledEvent
}