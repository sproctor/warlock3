package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.core.ClientEvent
import cc.warlock.warlock3.core.ClientOutputStyleEvent
import cc.warlock.warlock3.core.WarlockStyle
import cc.warlock.warlock3.stormfront.protocol.BaseElementListener
import cc.warlock.warlock3.stormfront.protocol.StartElement

class OutputHandler : BaseElementListener() {
    override fun startElement(element: StartElement): List<ClientEvent> {
        val className = element.attributes["class"]
        return listOf(ClientOutputStyleEvent(getStyleByClass(className)))
    }
}

fun getStyleByClass(name: String?): WarlockStyle? {
    return when (name) {
        "mono" -> WarlockStyle(monospace = true)
        else -> null
    }
}