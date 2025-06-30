package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.core.client.DialogObject
import warlockfe.warlock3.core.client.Percentage
import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StartElement
import warlockfe.warlock3.stormfront.protocol.StormfrontDialogObjectEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent
import warlockfe.warlock3.stormfront.util.parseDistance

class ProgressBarHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent? {
        val id = element.attributes["id"] ?: return null
        return StormfrontDialogObjectEvent(
            DialogObject.ProgressBar(
                id = id,
                value = Percentage(element.attributes["value"]?.toIntOrNull() ?: 0),
                text = element.attributes["text"],
                left = element.attributes["left"]?.let { parseDistance(it) },
                top = element.attributes["top"]?.let { parseDistance(it) },
                width = element.attributes["width"]?.let { parseDistance(it) },
                height = element.attributes["height"]?.let { parseDistance(it) },
                topAnchor = element.attributes["anchor_top"],
                leftAnchor = element.attributes["anchor_left"],
                tooltip = element.attributes["tooltip"],
            )
        )
    }
}
