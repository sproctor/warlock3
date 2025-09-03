package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythCliEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.util.CmdDefinition

class CliHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? {
        return WraythCliEvent(
            CmdDefinition(
                coord = element.attributes["coord"]!!,
                menu = element.attributes["menu"]!!,
                command = element.attributes["command"]!!,
                category = element.attributes["menu_cat"]!!,
            )
        )
    }
}