package cc.warlock.warlock3.stormfront.protocol.elements

import cc.warlock.warlock3.core.text.StyleRegistry
import cc.warlock.warlock3.stormfront.protocol.BaseElementListener
import cc.warlock.warlock3.stormfront.protocol.StartElement
import cc.warlock.warlock3.stormfront.protocol.StormfrontStyleEvent

class StyleHandler(private val styleRegistry: StyleRegistry) : BaseElementListener() {
    override fun startElement(element: StartElement): StormfrontStyleEvent {
        val style = element.attributes["id"]?.let { styleRegistry.getStyle(it) }
        return StormfrontStyleEvent(style)
    }
}