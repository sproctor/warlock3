package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythCmdTimestampEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent

class CmdTimestampHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? = element.attributes["data"]?.let { WraythCmdTimestampEvent(it) }
}
