package warlockfe.warlock3.wrayth.util

import io.ktor.network.selector.SelectorManager
import kotlin.coroutines.CoroutineContext

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

/**
 * Opens a TLS socket that verifies the server certificate against the system's default trust store
 * (i.e. a normal CA-valid cert), unlike [openTLSSocket] which pins a custom certificate. Used for
 * the MUD Mobile router, whose edge presents a standard public certificate.
 */
expect suspend fun openDefaultTlsSocket(
    selectorManager: SelectorManager,
    host: String,
    port: Int,
    coroutineContext: CoroutineContext,
): TLSSocketConnection
