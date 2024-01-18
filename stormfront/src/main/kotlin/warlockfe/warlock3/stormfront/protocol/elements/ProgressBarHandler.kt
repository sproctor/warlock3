package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.core.client.Percentage
import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StartElement
import warlockfe.warlock3.stormfront.protocol.StormfrontEvent
import warlockfe.warlock3.stormfront.protocol.StormfrontProgressBarEvent

class ProgressBarHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontEvent? {
        val id = element.attributes["id"] ?: return null
        val value = element.attributes["value"]?.toIntOrNull()?.let { Percentage(it) } ?: return null
        val text = element.attributes["text"] ?: ""
        val left = element.attributes["left"]?.toPercentage() ?: return null
        val width = element.attributes["width"]?.toPercentage() ?: return null
        return StormfrontProgressBarEvent(
            id = id,
            value = value,
            text = text,
            left = left,
            width = width,
        )
    }
}

fun String.toPercentage(): Percentage? {
    return dropLast(1).toIntOrNull()?.let { Percentage(it) }
}