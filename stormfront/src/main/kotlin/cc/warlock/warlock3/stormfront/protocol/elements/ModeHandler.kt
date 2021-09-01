package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.core.ClientEvent
import cc.warlock.warlock3.core.ClientPropertyChangedEvent
import cc.warlock.warlock3.stormfront.protocol.BaseElementListener
import cc.warlock.warlock3.stormfront.protocol.StartElement

class ModeHandler : BaseElementListener() {
    override fun startElement(element: StartElement): List<ClientEvent> {
        element.attributes["id"]?.let { mode ->
            return listOf(ClientPropertyChangedEvent("mode", mode))
        }
        return emptyList()
    }
}