package warlockfe.warlock3.core.script.wsl

import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.text.StyledStringSubstring
import warlockfe.warlock3.core.util.getIgnoringCase
import java.math.BigDecimal

class WslMonsterCount(
    private val client: WarlockClient
) : WslNumeric() {
    override fun toNumber(): BigDecimal {
        val roomObjs = client.components.value.getIgnoringCase("room objs") ?: return BigDecimal.ZERO
        var sum = 0
        roomObjs.substrings.forEach { substring ->
            if (substring is StyledStringSubstring) {
                if (substring.styles.any { it.name == "bold" }) {
                    sum++
                }
            }
        }
        return BigDecimal(sum)
    }
}