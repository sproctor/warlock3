package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythHandledEvent
import warlockfe.warlock3.wrayth.protocol.WraythRightEvent

class RightHandler : BaseElementListener() {
    // Consumes the text so it is handed back on close, with the exist id and noun of what is held.
    override fun characters(data: String): WraythEvent = WraythHandledEvent

    override fun endElement(
        attributes: Map<String, String>,
        text: String,
    ): WraythEvent? =
        if (text.isEmpty()) {
            null
        } else {
            WraythRightEvent(value = text, exist = attributes["exist"], noun = attributes["noun"])
        }
}
