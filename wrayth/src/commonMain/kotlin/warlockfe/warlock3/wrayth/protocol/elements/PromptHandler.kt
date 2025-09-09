package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.*
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythHandledEvent
import warlockfe.warlock3.wrayth.protocol.WraythPromptEvent
import warlockfe.warlock3.wrayth.protocol.WraythTimeEvent

class PromptHandler : ElementListener {
    // the following is undefined: <prompt> <prompt>foo</prompt> bar </prompt>
    private val prompt = StringBuilder()
    override fun startElement(element: StartElement): WraythEvent? {
        prompt.setLength(0)
        return element.attributes["time"]?.toLongOrNull()?.let { time ->
            WraythTimeEvent(time = time)
        }
    }

    override fun characters(data: String): WraythEvent {
        prompt.append(data)
        return WraythHandledEvent
    }

    override fun endElement(): WraythEvent {
        return WraythPromptEvent(prompt.toString())
    }
}
