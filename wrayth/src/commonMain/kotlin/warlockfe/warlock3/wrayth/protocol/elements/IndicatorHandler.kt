package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythPropertyEvent

class IndicatorHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? {
        val visible = element.attributes["visible"] ?: return null
        val enabled = visible.startsWith("y", true)
        val iconId = element.attributes["id"] ?: return null
        // Drop "Icon" from the start
        if (iconId.length <= 4) return null
        val status = iconId.drop(4)
        return WraythPropertyEvent(
            key = status.lowercase(),
            value = if (enabled) "1" else null
        )
    }
}