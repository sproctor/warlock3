package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.WraythSpellEvent

class SpellHandler : BaseElementListener() {
    override fun characters(data: String) = WraythSpellEvent(data)
}