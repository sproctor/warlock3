package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythEndCmdList
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythStartCmdList

class CmdlistHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? = WraythStartCmdList

    override fun endElement(): WraythEvent? = WraythEndCmdList
}
