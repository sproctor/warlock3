package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.*
import warlockfe.warlock3.wrayth.protocol.WraythActionEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythHandledEvent

class DHandler : BaseElementListener() {
    private val stringBuilder = StringBuilder()
    private var command: String? = null

    override fun startElement(element: StartElement): WraythEvent {
        command = element.attributes["cmd"]
        return WraythHandledEvent
    }

    override fun characters(data: String): WraythEvent {
        stringBuilder.append(data)
        return WraythHandledEvent
    }

    override fun endElement(): WraythEvent {
        val text = stringBuilder.toString()
        stringBuilder.clear()
        return WraythActionEvent(text, command ?: text)
    }
}