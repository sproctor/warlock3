package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.stormfront.protocol.BaseElementListener
import cc.warlock.warlock3.stormfront.protocol.StartElement
import cc.warlock.warlock3.stormfront.protocol.StormfrontEvent
import cc.warlock.warlock3.stormfront.protocol.StormfrontPropertyEvent

class IndicatorHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent? {
        val visible = element.attributes["visible"] ?: return null
        val enabled = visible.startsWith("y", true)
        val iconId = element.attributes["id"] ?: return null
        // Drop "Icon" from the start
        if (iconId.length <= 4) return null
        val status = iconId.drop(4)
        return StormfrontPropertyEvent(
            key = status.lowercase(),
            value = if (enabled) "1" else null
        )
    }
}