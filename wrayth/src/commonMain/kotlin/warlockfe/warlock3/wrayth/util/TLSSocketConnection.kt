package warlockfe.warlock3.wrayth.util

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel

data class TLSSocketConnection(
    val readChannel: ByteReadChannel,
    val writeChannel: ByteWriteChannel,
    val close: () -> Unit,
)
