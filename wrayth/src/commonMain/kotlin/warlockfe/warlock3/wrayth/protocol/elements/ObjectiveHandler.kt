package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythObjectiveChildEvent
import warlockfe.warlock3.wrayth.protocol.WraythObjectiveEvent

class ObjectiveHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent = WraythObjectiveEvent(element.attributes)
}

/** `<task>`, `<reward>` and `<action>`, which belong to the `<objective>` before them. */
class ObjectiveChildHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent = WraythObjectiveChildEvent(element.name, element.attributes)
}
