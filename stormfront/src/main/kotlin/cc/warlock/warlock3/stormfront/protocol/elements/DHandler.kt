package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.stormfront.protocol.*

class DHandler : BaseElementListener() {
    private val stringBuilder = StringBuilder()

    override fun startElement(element: StartElement): StormfrontEvent {
        return StormfrontHandledEvent
    }

    override fun characters(data: String): StormfrontEvent {
        stringBuilder.append(data)
        return StormfrontHandledEvent
    }

    override fun endElement(): StormfrontEvent {
        val text = stringBuilder.toString()
        stringBuilder.clear()
        return StormfrontActionEvent(text, text)
    }
}