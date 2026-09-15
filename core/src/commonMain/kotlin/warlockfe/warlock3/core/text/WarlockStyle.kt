package warlockfe.warlock3.core.text

import warlockfe.warlock3.core.client.WarlockAction

data class WarlockStyle(
    val name: String,
    val action: WarlockAction? = null,
    // An inline style for text whose look comes from the game rather than from a named preset: an
    // ANSI color from a telnet MUD, say. The renderer still looks [name] up in the preset map first,
    // so a skin or the user can override a named inline style; [layer] is what applies when nothing
    // by that name is defined. Null for the ordinary preset-only styles.
    val layer: StyleLayer? = null,
) {
    companion object {
        val Bold = WarlockStyle("bold")
        val Command = WarlockStyle("command")
        val Echo = WarlockStyle("echo")
        val Error = WarlockStyle("error")
        val Link = { action: WarlockAction? -> WarlockStyle("link", action) }
        val RoomName = WarlockStyle("roomName")
        val Speech = WarlockStyle("speech")
        val Thought = WarlockStyle("thought")
        val Watching = WarlockStyle("watching")
        val Whisper = WarlockStyle("whisper")
        val Default = WarlockStyle("")

        // The customizable style presets, in display order. The appearance settings edit this fixed,
        // known set rather than whatever presets the skin happens to define. [Default] is deliberately
        // excluded: the base ("default text") style is not a preset — it lives in the character settings
        // (color + font) and is edited separately.
        val presets: List<WarlockStyle> =
            listOf(RoomName, Bold, Speech, Whisper, Thought, Watching, Command, Echo, Error, Link(null))
    }
}
