package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.core.ClientEvent
import cc.warlock.warlock3.core.ClientRemoveStyleEvent
import cc.warlock.warlock3.core.WarlockStyle
import cc.warlock.warlock3.stormfront.protocol.BaseElementListener
import cc.warlock.warlock3.stormfront.protocol.StartElement

class PopBoldHandler  : BaseElementListener() {
    override fun startElement(element: StartElement): List<ClientEvent> {
        return listOf(ClientRemoveStyleEvent(WarlockStyle(name = "bold")))
    }
}