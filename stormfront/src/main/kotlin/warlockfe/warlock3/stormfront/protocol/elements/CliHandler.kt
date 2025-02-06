package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StartElement
import warlockfe.warlock3.stormfront.protocol.StormfrontCliEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent
import warlockfe.warlock3.stormfront.util.CmdDefinition

class CliHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent? {
        return StormfrontCliEvent(
            CmdDefinition(
                coord = element.attributes["coord"]!!,
                menu = element.attributes["menu"]!!,
                command = element.attributes["command"]!!,
                category = element.attributes["menu_cat"]!!,
            )
        )
    }
}