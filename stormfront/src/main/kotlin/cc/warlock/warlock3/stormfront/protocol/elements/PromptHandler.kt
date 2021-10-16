package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.stormfront.protocol.*

class PromptHandler : ElementListener {
    override fun startElement(element: StartElement): StormfrontEvent? {
        return element.attributes["time"]?.let { time ->
            StormfrontTimeEvent(time = time)
        }
    }

    override fun characters(data: String): StormfrontEvent {
        return StormfrontPromptEvent(data)
    }

    override fun endElement(element: EndElement): StormfrontEvent? {
        return null
    }
}
