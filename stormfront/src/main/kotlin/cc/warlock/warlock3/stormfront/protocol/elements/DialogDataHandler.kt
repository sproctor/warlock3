package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.stormfront.protocol.*

class DialogDataHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent {
        return StormfrontDialogDataEvent(element.attributes["id"])
    }

    override fun endElement(element: EndElement): StormfrontEvent {
        return StormfrontDialogDataEvent(null)
    }
}