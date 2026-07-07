package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythOutputEvent

class OutputHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythOutputEvent = WraythOutputEvent(className = element.attributes["class"])
}
