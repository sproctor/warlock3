package cc.warlock.warlock3.core.text

@JvmInline
value class WarlockStyle(val name: String) {
    companion object {
        val Bold = WarlockStyle("bold")
        val Command = WarlockStyle("command")
        val Echo = WarlockStyle("echo")
        val Error = WarlockStyle("error")
        val Mono = WarlockStyle("mono")
        val RoomName = WarlockStyle("roomName")
        val Speech = WarlockStyle("speech")
        val Thought = WarlockStyle("thought")
        val Watching = WarlockStyle("watching")
        val Whisper = WarlockStyle("whisper")
    }
}