package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.core.client.DialogObject
import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythDialogObjectEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.util.parseDistance

class DropDownBoxHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? {
        val id = element.attributes["id"] ?: return null
        // The display labels and their command values are parallel comma-separated lists; when only
        // one is given, use it for both.
        val texts = element.attributes["content_text"]?.split(",") ?: emptyList()
        val values = element.attributes["content_value"]?.split(",") ?: texts
        val options =
            texts.mapIndexed { index, text ->
                DialogObject.DropDownBox.Option(text = text, value = values.getOrElse(index) { text })
            }
        return WraythDialogObjectEvent(
            DialogObject.DropDownBox(
                id = id,
                value = element.attributes["value"],
                cmd = element.attributes["cmd"],
                options = options,
                left = element.attributes["left"]?.let { parseDistance(it) },
                top = element.attributes["top"]?.let { parseDistance(it) },
                width = element.attributes["width"]?.let { parseDistance(it) },
                height = element.attributes["height"]?.let { parseDistance(it) },
                align = element.attributes["align"],
                topAnchor = element.attributes["anchor_top"],
                leftAnchor = element.attributes["anchor_left"],
                tooltip = element.attributes["tooltip"],
            ),
        )
    }
}
