package cc.warlock.warlock3.core.text

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import java.util.*

class StyleRegistry(
    caseSensitiveStyles: Flow<Map<String, WarlockStyle>>,
    scope: CoroutineScope,
    val saveStyle: (String, WarlockStyle) -> Unit,
) {
    val styles = caseSensitiveStyles.map {
        TreeMap<String, WarlockStyle>(String.CASE_INSENSITIVE_ORDER).apply {
            putAll(defaultStyles)
            putAll(it)
        }
    }
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = emptyMap())
    val boldStyle: WarlockStyle
        get() = getStyle("bold")
    val commandStyle: WarlockStyle
        get() = styles.value["command"] ?: WarlockStyle()
    val errorStyle: WarlockStyle
        get() = styles.value["error"] ?: WarlockStyle()
    private val defaultStyles =
        mapOf(
            "bold" to WarlockStyle(
                textColor = WarlockColor("#FFFF00")
            ),
            "command" to WarlockStyle(
                textColor = WarlockColor("#FFFFFF"),
                backgroundColor = WarlockColor("#404040")
            ),
            "echo" to WarlockStyle(
                textColor = WarlockColor("#FFFF80")
            ),
            "error" to WarlockStyle(
                textColor = WarlockColor(red = 0xFF, green = 0, blue = 0)
            ),
            "roomName" to WarlockStyle(
                textColor = WarlockColor("#FFFFFF"),
                backgroundColor = WarlockColor("#0000FF"),
                entireLine = true
            ),
            "speech" to WarlockStyle(
                textColor = WarlockColor("#80FF80")
            ),
            "thought" to WarlockStyle(
                textColor = WarlockColor("#FF8000")
            ),
            "watching" to WarlockStyle(
                textColor = WarlockColor("#FFFF00")
            ),
            "whisper" to WarlockStyle(
                textColor = WarlockColor("#80FFFF")
            ),
        )
    fun getStyle(name: String): WarlockStyle {
        return styles.value[name] ?: WarlockStyle()
    }
}