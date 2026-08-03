package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythDialogWindowEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.util.WraythDialogWindow

class OpenDialogHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? {
        val id = element.attributes["id"] ?: return null
        return WraythDialogWindowEvent(
            WraythDialogWindow(
                id = id,
                // Titles use Windows menu-mnemonic escaping: "Friends && Enemies" displays a
                // single ampersand.
                title = (element.attributes["title"] ?: id).replace("&&", "&"),
                type = element.attributes["type"],
                location = element.attributes["location"],
                resident = element.attributes["resident"]?.startsWith(prefix = "t", ignoreCase = true) == true,
            ),
        )
    }
}
