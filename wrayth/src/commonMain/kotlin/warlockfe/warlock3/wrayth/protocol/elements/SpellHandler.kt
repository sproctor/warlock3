package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.WraythPropertyEvent

class SpellHandler : BaseElementListener() {
    override fun characters(data: String) = WraythPropertyEvent("spell", data)
}