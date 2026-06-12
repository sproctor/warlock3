package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythHandledEvent
import warlockfe.warlock3.wrayth.protocol.WraythPromptEvent
import warlockfe.warlock3.wrayth.protocol.WraythTimeEvent

class PromptHandler : BaseElementListener() {
    // The time attribute (if any) is emitted on open; the prompt text is accumulated by the protocol
    // handler and supplied on close, so this listener holds no state.
    override fun startElement(element: StartElement): WraythEvent? =
        element.attributes["time"]?.toLongOrNull()?.let { time ->
            WraythTimeEvent(time = time)
        }

    override fun characters(data: String): WraythEvent = WraythHandledEvent

    override fun endElement(
        attributes: Map<String, String>,
        text: String,
    ): WraythEvent = WraythPromptEvent(text)
}
