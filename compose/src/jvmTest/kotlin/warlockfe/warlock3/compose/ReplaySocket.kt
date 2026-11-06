package warlockfe.warlock3.compose

import warlockfe.warlock3.core.client.WarlockSocket

/** A game log being replayed: [lines], then the end of the stream. */
class ReplaySocket(
    lines: List<String>,
) : WarlockSocket {
    private val remaining = ArrayDeque(lines)

    override var isClosed = false

    override suspend fun connect(
        host: String,
        port: Int,
    ) = Unit

    override suspend fun readLine(): String? = remaining.removeFirstOrNull()

    override suspend fun readAvailable(min: Int): String? = readLine()

    override fun ready(): Boolean = remaining.isNotEmpty()

    override suspend fun write(text: String) = Unit

    override fun close() {
        isClosed = true
    }
}
