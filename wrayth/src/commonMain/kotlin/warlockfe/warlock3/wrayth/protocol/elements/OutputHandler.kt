package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythOutputEvent

class OutputHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythOutputEvent {
        val className = element.attributes["class"]
        return WraythOutputEvent(style = className?.ifBlank { null }?.let { WarlockStyle(it) })
    }
}
