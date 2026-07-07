package warlockfe.warlock3.core.text

import warlockfe.warlock3.core.client.WarlockAction

data class WarlockStyle(
    val name: String,
    val action: WarlockAction? = null,
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
        // known set rather than whatever presets the skin happens to define.
        val presets: List<WarlockStyle> =
            listOf(Default, RoomName, Bold, Speech, Whisper, Thought, Watching, Command, Echo, Error, Link(null))
    }
}
