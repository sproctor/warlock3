package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StartElement
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontUpdateVerbsEvent

class UpdateVerbsHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent? {
        return StormfrontUpdateVerbsEvent
    }
}