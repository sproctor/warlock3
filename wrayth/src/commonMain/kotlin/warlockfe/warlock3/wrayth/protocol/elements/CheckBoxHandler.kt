package warlockfe.warlock3.wrayth.protocol.elements

import warlockfe.warlock3.core.client.PanelObject
import warlockfe.warlock3.wrayth.protocol.BaseElementListener
import warlockfe.warlock3.wrayth.protocol.StartElement
import warlockfe.warlock3.wrayth.protocol.WraythDialogObjectEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.util.parseDistance

class CheckBoxHandler : BaseElementListener() {
    override fun startElement(element: StartElement): WraythEvent? {
        val id = element.attributes["id"] ?: return null
        // A checkbox exists to hand a value to another widget's command, so one that carries no
        // values has nothing to say. The real client agrees and draws nothing at all for it, not
        // even an empty box, so it is dropped here rather than rendered as a dead toggle.
        val checkedValue = element.attributes["checked_value"] ?: return null
        val uncheckedValue = element.attributes["unchecked_value"] ?: return null
        return WraythDialogObjectEvent(
            PanelObject.CheckBox(
                id = id,
                text = element.attributes["text"],
                // Presence, not value: the client ticks the box for `checked='off'` and
                // `checked='false'` exactly as it does for `checked='true'`, and leaves it clear
                // only when the attribute is absent entirely. Game data sends `checked=''` for a
                // ticked box, which is why reading the value would invert half of them.
                checked = element.attributes.containsKey("checked"),
                checkedValue = checkedValue,
                uncheckedValue = uncheckedValue,
                tooltip = element.attributes["tooltip"],
                left = element.attributes["left"]?.let { parseDistance(it) },
                top = element.attributes["top"]?.let { parseDistance(it) },
                width = element.attributes["width"]?.let { parseDistance(it) },
                height = element.attributes["height"]?.let { parseDistance(it) },
                align = element.attributes["align"],
                topAnchor = element.attributes["anchor_top"],
                leftAnchor = element.attributes["anchor_left"],
            ),
        )
    }
}
