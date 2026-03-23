package warlockfe.warlock3.wrayth.util

import io.ktor.network.selector.SelectorManager
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlin.coroutines.CoroutineContext

data class TLSSocketConnection(
    val readChannel: ByteReadChannel,
    val writeChannel: ByteWriteChannel,
    val close: () -> Unit,
)

expect suspend fun openTLSSocket(
    selectorManager: SelectorManager,
    host: String,
    port: Int,
    certificate: ByteArray,
    coroutineContext: CoroutineContext,
): TLSSocketConnection

expect suspend fun openPlainSocket(
    selectorManager: SelectorManager,
    host: String,
    port: Int,
    coroutineContext: CoroutineContext,
): TLSSocketConnection
