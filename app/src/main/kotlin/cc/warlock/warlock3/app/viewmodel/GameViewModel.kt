package cc.warlock.warlock3.app.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import cc.warlock.warlock3.core.*
import cc.warlock.warlock3.core.wsl.WslScript
import cc.warlock.warlock3.core.wsl.WslScriptInstance
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File

class GameViewModel(
    val client: StormfrontClient
) {
    private val _properties = MutableStateFlow<Map<String, String>>(emptyMap())
    val properties = _properties.asStateFlow()

    val components = client.components

    private val _sendHistory = MutableStateFlow<List<String>>(emptyList())
    val sendHistory = _sendHistory.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private val scriptInstances = MutableStateFlow<List<ScriptInstance>>(emptyList())

    val windows = client.windows

    val openWindows = client.openWindows

    val defaultBackgroundColor = MutableStateFlow(Color.DarkGray)
    val defaultTextColor = MutableStateFlow(Color.White)

    fun send(line: String) {
        _sendHistory.value = listOf(line) + _sendHistory.value
        if (line.startsWith(".")) {
            val scriptName = line.drop(1)
            val scriptDir = System.getProperty("user.home") + "/.warlock3/scripts"
            val file = File("$scriptDir/$scriptName.wsl")
            if (file.exists()) {
                client.print(StyledString("File exists"))
                val script = WslScript(name = scriptName, file = file)
                val scriptInstance = WslScriptInstance(name = scriptName, script = script)
                scriptInstance.start(client, emptyList())
            } else {
                client.print(StyledString("Could not find a script with that name"))
            }
        } else {
            client.sendCommand(line)
        }
    }

    fun showWindow(name: String) {
        client.showWindow(name)
    }

    fun hideWindow(name: String) {
        client.hideWindow(name)
    }
}

fun WarlockColor.toColor(): Color {
    return Color(red = red, green = green, blue = blue)
}

fun StyledString.toAnnotatedString(variables: Map<String, StyledString>): AnnotatedString {
    return substrings.map { it.toAnnotatedString(variables) }.reduceOrNull { acc, annotatedString ->
        acc + annotatedString
    } ?: AnnotatedString("")
}

fun WarlockStyle.toSpanStyle(): SpanStyle {
    return SpanStyle(
        color = textColor?.toColor() ?: Color.Unspecified,
        background = backgroundColor?.toColor() ?: Color.Unspecified,
        fontFamily = if (monospace) FontFamily.Monospace else null,
        textDecoration = if (underline) TextDecoration.Underline else null,
    )
}

fun StyledStringLeaf.toAnnotatedString(variables: Map<String, StyledString>): AnnotatedString {
    // FIXME: break circular references
    return when (this) {
        is StyledStringSubstring ->
            AnnotatedString(text = text, spanStyle = style?.toSpanStyle() ?: SpanStyle())
        is StyledStringVariable ->
            variables[name]?.toAnnotatedString(variables) ?: AnnotatedString("")
    }
}