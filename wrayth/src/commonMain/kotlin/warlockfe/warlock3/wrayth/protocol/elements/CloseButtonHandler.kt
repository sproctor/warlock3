package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.core.client.PanelObject
import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythDialogObjectEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.util.parseDistance

class CloseButtonHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? {
        val id = element.attributes["id"] ?: return null
        // The caption is the whole button: the real client draws nothing for a close button with no
        // `value`, not even an empty frame at the size the wire asked for. There is no "Close"
        // fallback.
        if (element.attributes["value"] == null) return null
        return WraythDialogObjectEvent(
            PanelObject.Button(
                id = id,
                value = element.attributes["value"],
                cmd = element.attributes["cmd"],
                closesPanel = true,
                tooltip = element.attributes["tooltip"],
                echo = element.attributes["echo"],
                left = element.attributes["left"]?.let { parseDistance(it) },
                top = element.attributes["top"]?.let { parseDistance(it) },
                width = element.attributes["width"]?.let { parseDistance(it) },
                height = element.attributes["height"]?.let { parseDistance(it) },
                align = element.attributes["align"],
                topAnchor = element.attributes["anchor_top"],
                leftAnchor = element.attributes["anchor_left"],
            ),
        )
    }
}
