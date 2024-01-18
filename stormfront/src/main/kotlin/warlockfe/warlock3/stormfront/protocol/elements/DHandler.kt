package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.stormfront.protocol.*
import warlockfe.warlock3.stormfront.protocol.StormfrontActionEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontHandledEvent

class DHandler : BaseElementListener() {
    private val stringBuilder = StringBuilder()
    private var command: String? = null

    override fun startElement(element: StartElement): StormfrontEvent {
        command = element.attributes["cmd"]
        return StormfrontHandledEvent
    }

    override fun characters(data: String): StormfrontEvent {
        stringBuilder.append(data)
        return StormfrontHandledEvent
    }

    override fun endElement(): StormfrontEvent {
        val text = stringBuilder.toString()
        stringBuilder.clear()
        return StormfrontActionEvent(text, command ?: text)
    }
}