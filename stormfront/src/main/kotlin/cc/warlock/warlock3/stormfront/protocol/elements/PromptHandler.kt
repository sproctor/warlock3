package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.stormfront.protocol.*

class PromptHandler : ElementListener {
    // the following is undefined: <prompt> <prompt>foo</prompt> bar </prompt>
    private val prompt = StringBuilder()
    override fun startElement(element: StartElement): StormfrontEvent? {
        prompt.setLength(0)

        return element.attributes["time"]?.let { time ->
            StormfrontTimeEvent(time = time)
        }
    }
    override fun characters(data: String): StormfrontEvent {
        prompt.append(data)
        return StormfrontHandledEvent
    }
    override fun endElement(element: EndElement): StormfrontPromptEvent {
       return StormfrontPromptEvent(prompt.toString())
    }
}
