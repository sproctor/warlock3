package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythCreatureStatusEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent

// <crtrStatus exist="65662200" hostile="0" disengaged="1"/>: what the game says about one creature.
// DragonRealms sends it; the attributes besides exist are passed on as they are.
class CrtrStatusHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? {
        val exist = element.attributes["exist"] ?: return null
        return WraythCreatureStatusEvent(exist = exist, attributes = element.attributes - "exist")
    }
}
