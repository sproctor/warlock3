package warlockfe.warlock3.wrayth.util

import io.ktor.network.selector.SelectorManager
import kotlin.coroutines.CoroutineContext

/**
 * Opens a TLS connection. With a [certificate] the connection is pinned to it (the SGE login
 * server's CA, which has no hostname in it, so that path skips hostname checking); with null the
 * platform's trust store decides, as for any other server on the internet, and the server name is
 * checked.
 */
expect suspend fun openTLSSocket(
    selectorManager: SelectorManager,
    host: String,
    port: Int,
    certificate: ByteArray?,
    coroutineContext: CoroutineContext,
): TLSSocketConnection

expect suspend fun openPlainSocket(
    selectorManager: SelectorManager,
    host: String,
    port: Int,
    coroutineContext: CoroutineContext,
): TLSSocketConnection
