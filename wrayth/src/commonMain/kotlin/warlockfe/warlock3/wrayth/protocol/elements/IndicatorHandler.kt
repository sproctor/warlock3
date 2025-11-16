package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythIndicatorEvent

class IndicatorHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? {
        val visible = element.attributes["visible"] ?: return null
        val iconId = element.attributes["id"]?.lowercase() ?: return null

        // Drop "Icon" from the start
        return WraythIndicatorEvent(
            iconId = iconId.removePrefix("icon"),
            visible = visible.startsWith("y", true),
        )
    }
}