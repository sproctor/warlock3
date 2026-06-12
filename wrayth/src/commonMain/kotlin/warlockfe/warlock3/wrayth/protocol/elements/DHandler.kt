package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythActionEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythHandledEvent

class DHandler : BaseElementListener() {
    // Consume the start tag and the inner text; the command and accumulated text are supplied on close
    // by the protocol handler, so this listener holds no state and is safe to share across elements.
    override fun startElement(element: StartElement): WraythEvent = WraythHandledEvent

    override fun characters(data: String): WraythEvent = WraythHandledEvent

    override fun endElement(
        attributes: Map<String, String>,
        text: String,
    ): WraythEvent = WraythActionEvent(text, attributes["cmd"] ?: text)
}
