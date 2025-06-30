package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StartElement
import warlockfe.warlock3.stormfront.protocol.StormfrontDialogWindowEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent
import warlockfe.warlock3.stormfront.util.StormfrontDialogWindow

class OpenDialogHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent? {
        // We receive a location here that we're ignoring to have a bit more control over where things are placed
        val id = element.attributes["id"] ?: return null
        return StormfrontDialogWindowEvent(
            StormfrontDialogWindow(
                id = id,
                title = element.attributes["title"] ?: id,
            )
        )
    }
}