package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.core.client.CreatureStatus
import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythCreatureStatusEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent

class CrtrStatusHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? {
        val attributes = element.attributes
        val exist = attributes["exist"] ?: return null
        // Any flag the tag doesn't name a field for is an effect when it is 1 (stunned="1", ...).
        val effects =
            attributes
                .filter { (name, value) -> name !in NAMED_ATTRIBUTES && value == "1" }
                .keys
                .toMutableList()
        attributes["condition"]?.takeIf { it.isNotEmpty() }?.let { effects += it }
        return WraythCreatureStatusEvent(
            CreatureStatus(
                exist = exist,
                hostile = attributes["hostile"] == "1",
                disengaged = attributes["disengaged"] == "1",
                effects = effects,
                health = attributes["health"]?.toIntOrNull(),
                maxHealth = attributes["maxhealth"]?.toIntOrNull(),
            ),
        )
    }

    private companion object {
        val NAMED_ATTRIBUTES = setOf("exist", "hostile", "disengaged", "condition", "health", "maxhealth")
    }
}
