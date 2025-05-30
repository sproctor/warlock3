package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StartElement
import warlockfe.warlock3.stormfront.protocol.StormfrontEndCmdList
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontStartCmdList

class CmdlistHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent? {
        return StormfrontStartCmdList
    }

    override fun endElement(): StormfrontEvent? {
        return StormfrontEndCmdList
    }
}
