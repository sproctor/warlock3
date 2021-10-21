package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.core.text.WarlockStyle
import cc.warlock.warlock3.stormfront.protocol.BaseElementListener
import cc.warlock.warlock3.stormfront.protocol.StartElement
import cc.warlock.warlock3.stormfront.protocol.StormfrontOutputEvent

class OutputHandler : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontOutputEvent {
        val className = element.attributes["class"]
        return StormfrontOutputEvent(style = getStyleByClass(className))
    }
}

fun getStyleByClass(name: String?): WarlockStyle? {
    return when (name) {
        "mono" -> WarlockStyle(monospace = true)
        else -> null
    }
}