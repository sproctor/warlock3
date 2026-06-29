package warlockfe.warlock3.scripting.wsl

import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.text.StyledStringSubstring

class WslMonsterCount(
    private val client: WarlockClient,
) : WslNumeric() {
    override fun toNumber(): Double {
        val roomObjs = client.getComponent("room objs") ?: return 0.0
        var sum = 0
        roomObjs.substrings.forEach { substring ->
            if (substring is StyledStringSubstring) {
                if (substring.styles.any { it.name == "bold" }) {
                    sum++
                }
            }
        }
        return sum.toDouble()
    }
}
