package warlockfe.warlock3.stormfront.protocol.elements

import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.stormfront.protocol.BaseElementListener
import warlockfe.warlock3.stormfront.protocol.StartElement
import warlockfe.warlock3.stormfront.protocol.StormfrontOutputEvent

class OutputHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontOutputEvent {
        val className = element.attributes["class"]
        return StormfrontOutputEvent(style = getStyleByClass(className))
    }
}

fun getStyleByClass(name: String?): WarlockStyle? {
    return if (name?.isNotBlank() == true) {
        WarlockStyle(name)
    } else {
        null
    }
}