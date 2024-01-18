package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.stormfront.protocol.*
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontHandledEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontPromptEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontTimeEvent

class PromptHandler : ElementListener {
    // the following is undefined: <prompt> <prompt>foo</prompt> bar </prompt>
    private val prompt = StringBuilder()
    override fun startElement(element: StartElement): StormfrontEvent? {
        return element.attributes["time"]?.let { time ->
            prompt.setLength(0)
            StormfrontTimeEvent(time = time)
        }
    }

    override fun characters(data: String): StormfrontEvent {
        prompt.append(data)
        return StormfrontHandledEvent
    }

    override fun endElement(): StormfrontEvent {
        return StormfrontPromptEvent(prompt.toString())
    }
}
