package warlockfe.warlock3.core.network

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel

data class SocketConnection(
    val readChannel: ByteReadChannel,
    val writeChannel: ByteWriteChannel,
    val close: () -> Unit,
)
