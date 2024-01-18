package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.stormfront.protocol.*
import warlockfe.warlock3.stormfront.protocol.StormfrontDialogDataEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent

class DialogDataHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent {
        return StormfrontDialogDataEvent(element.attributes["id"])
    }

    override fun endElement(): StormfrontEvent {
        return StormfrontDialogDataEvent(null)
    }
}