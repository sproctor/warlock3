package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.core.ClientAddStyleEvent
import cc.warlock.warlock3.core.ClientEvent
import cc.warlock.warlock3.core.WarlockStyle
import cc.warlock.warlock3.stormfront.protocol.BaseElementListener
import cc.warlock.warlock3.stormfront.protocol.StartElement

class PushBoldHandler : BaseElementListener() {
    override fun startElement(element: StartElement): List<ClientEvent> {
        return listOf(ClientAddStyleEvent(WarlockStyle(name = "bold")))
    }
}