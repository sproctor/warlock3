package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.core.ClientAddStyleEvent
import cc.warlock.warlock3.core.ClientClearStyleEvent
import cc.warlock.warlock3.core.ClientEvent
import cc.warlock.warlock3.core.WarlockStyle
import cc.warlock.warlock3.stormfront.protocol.BaseElementListener
import cc.warlock.warlock3.stormfront.protocol.StartElement

class StyleHandler : BaseElementListener() {
    override fun startElement(element: StartElement): List<ClientEvent> {
        val id = element.attributes["id"]
        return listOf(if (id != null) {
            ClientAddStyleEvent(WarlockStyle(name = id))
        } else {
            ClientClearStyleEvent
        })
    }
}