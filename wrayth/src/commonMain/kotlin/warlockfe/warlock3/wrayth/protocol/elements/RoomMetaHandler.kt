package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.core.client.RoomMeta
import warlockfe.warlock3.core.client.RoomWeather
import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythRoomMetaEvent

class RoomMetaHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? {
        val attributes = element.attributes
        // Without both of these the tag says nothing about the room.
        val climate = attributes["climate"]?.toIntOrNull() ?: return null
        val terrain = attributes["terrain"]?.toIntOrNull() ?: return null

        fun flag(name: String): Boolean? = attributes[name]?.let { it == "1" || it == "y" || it == "true" }
        val waterLevel =
            attributes["water"]?.toIntOrNull()
                ?: when {
                    flag("water3") == true -> 3
                    flag("water2") == true -> 2
                    flag("water1") == true -> 1
                    else -> 0
                }
        return WraythRoomMetaEvent(
            RoomMeta(
                climate = climate,
                terrain = terrain,
                realm = attributes["realm"]?.toIntOrNull(),
                inside = flag("inside"),
                sanctuary = flag("sanctuary") ?: false,
                bonfire = flag("bonfire") ?: false,
                locker = flag("locker") ?: false,
                waterLevel = waterLevel,
                weather =
                    when (attributes["weather"]?.toIntOrNull()) {
                        1 -> RoomWeather.STORM
                        2 -> RoomWeather.SNOW
                        else -> null
                    },
            ),
        )
    }
}
