package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.core.ClientEvent
import cc.warlock.warlock3.core.ClientPromptEvent
import cc.warlock.warlock3.core.ClientPropertyChangedEvent
import cc.warlock.warlock3.stormfront.protocol.ElementListener
import cc.warlock.warlock3.stormfront.protocol.EndElement
import cc.warlock.warlock3.stormfront.protocol.StartElement

class PromptHandler : ElementListener {
    // the following is undefined: <prompt> <prompt>foo</prompt> bar </prompt>
    private var prompt = StringBuilder()
    override fun startElement(element: StartElement): List<ClientEvent> {
        prompt.setLength(0)

        element.attributes["time"]?.let { time ->
            return listOf(ClientPropertyChangedEvent("time", time))
        }
        return emptyList()
    }
    override fun characters(data: String): List<ClientEvent> {
        prompt.append(data)
        return emptyList()
    }
    override fun endElement(element: EndElement): List<ClientEvent> {
       return listOf(ClientPromptEvent(prompt.toString()))
    }
}