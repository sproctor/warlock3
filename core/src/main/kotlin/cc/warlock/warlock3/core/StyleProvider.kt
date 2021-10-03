package cc.warlock.warlock3.stormfront

import cc.warlock.warlock3.core.WarlockColor
import cc.warlock.warlock3.core.WarlockStyle

object StyleProvider {
    val boldStyle = WarlockStyle(
        textColor = WarlockColor(red = 0xFF, green = 0xFF, blue = 0x00)
    )
    val commandStyle = WarlockStyle(
        textColor = WarlockColor(red = 0xFF, green = 0xFF, blue = 0x00)
    )
    val errorStyle = WarlockStyle(
        textColor = WarlockColor(red = 0xFF, green = 0, blue = 0)
    )
    private val styles =
        mapOf(
            "bold" to boldStyle,
            "command" to commandStyle,
            "error" to errorStyle,
            "roomName" to WarlockStyle(
                backgroundColor = WarlockColor(red = 0, green = 0, blue = 0xFF),
                isEntireLineBackground = true
            )
        )
    fun getStyle(name: String): WarlockStyle {
        return styles[name] ?: WarlockStyle()
    }
}