package cc.warlock.warlock3.core.text

data class WarlockStyle(val name: String, val annotations: List<Pair<String, String>>? = null) {
    companion object {
        val Bold = WarlockStyle("bold")
        val Command = WarlockStyle("command")
        val Echo = WarlockStyle("echo")
        val Error = WarlockStyle("error")
        val Link = { annotation: Pair<String, String>? -> WarlockStyle("link", listOfNotNull(annotation)) }
        val Mono = WarlockStyle("mono")
        val RoomName = WarlockStyle("roomName")
        val Speech = WarlockStyle("speech")
        val Thought = WarlockStyle("thought")
        val Watching = WarlockStyle("watching")
        val Whisper = WarlockStyle("whisper")
    }
}