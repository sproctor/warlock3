package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.WraythPropertyEvent

class LeftHandler : BaseElementListener() {
    override fun characters(data: String) = WraythPropertyEvent("left", data)
}