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
