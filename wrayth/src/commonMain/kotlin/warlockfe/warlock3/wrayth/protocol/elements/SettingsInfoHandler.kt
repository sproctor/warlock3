package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythSettingsInfoEvent

class SettingsInfoHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent {
        return WraythSettingsInfoEvent(
            crc = element.attributes["crc"],
            instance = element.attributes["instance"],
        )
    }
}
