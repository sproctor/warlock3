@file:OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class)

package warlockfe.warlock3.wrayth.util

import co.touchlab.kermit.Logger
import io.ktor.network.selector.SelectorManager
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreFoundation.CFArrayCreate
import platform.CoreFoundation.CFArrayRef
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFTypeArrayCallBacks
import platform.Network.nw_connection_cancel
import platform.Network.nw_connection_create
import platform.Network.nw_connection_receive
import platform.Network.nw_connection_send
import platform.Network.nw_connection_set_queue
import platform.Network.nw_connection_set_state_changed_handler
import platform.Network.nw_connection_start
import platform.Network.nw_connection_state_cancelled
import platform.Network.nw_connection_state_failed
import platform.Network.nw_connection_state_ready
import platform.Network.nw_connection_t
import platform.Network.nw_content_context_create
import platform.Network.nw_content_context_t
import platform.Network.nw_endpoint_create_host
import platform.Network.nw_error_domain_dns
import platform.Network.nw_error_domain_posix
import platform.Network.nw_error_domain_tls
import platform.Network.nw_error_get_error_code
import platform.Network.nw_error_get_error_domain
import platform.Network.nw_error_t
import platform.Network.nw_parameters_configure_protocol_block_t
import platform.Network.nw_parameters_copy_default_protocol_stack
import platform.Network.nw_parameters_create
import platform.Network.nw_protocol_options_t
import platform.Network.nw_protocol_stack_prepend_application_protocol
import platform.Network.nw_protocol_stack_set_transport_protocol
import platform.Network.nw_tcp_create_options
import platform.Network.nw_tls_copy_sec_protocol_options
import platform.Network.nw_tls_create_options
import platform.Security.SecCertificateCreateWithData
import platform.Security.SecCertificateRef
import platform.Security.SecPolicyCreateBasicX509
import platform.Security.SecPolicyRef
import platform.Security.SecTrustEvaluateWithError
import platform.Security.SecTrustSetAnchorCertificates
import platform.Security.SecTrustSetAnchorCertificatesOnly
import platform.Security.SecTrustSetPolicies
import platform.Security.errSecSuccess
import platform.Security.sec_protocol_options_set_tls_server_name
import platform.Security.sec_protocol_options_set_verify_block
import platform.Security.sec_trust_copy_ref
import platform.Security.sec_trust_t
import platform.darwin.dispatch_data_create
import platform.darwin.dispatch_data_create_map
import platform.darwin.dispatch_data_t
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t
import platform.posix.memcpy
import platform.posix.size_tVar
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS sockets on Network.framework.
 *
 * This replaces a hand-rolled SecureTransport implementation that drove a raw BSD socket with
 * blocking read()/write() on background coroutines. That version was deprecated by Apple, and its
 * teardown was the hard part: the SSL context, the stable ref its IO callbacks read the descriptor
 * from, and the descriptor itself all had to be released in the right order, only after two jobs
 * parked inside blocking native calls had actually finished.
 *
 * Network.framework removes that whole category of problem. `nw_connection_t` and friends are
 * Objective-C objects, so Kotlin/Native's runtime owns their lifetime -- there is nothing to
 * CFRelease. Cancellation is asynchronous by design, and both IO paths are callbacks bridged
 * straight into coroutines, so nothing blocks a thread waiting on the network.
 *
 * Certificate pinning keeps the previous semantics exactly: the pinned certificate is added as an
 * anchor, system anchors remain trusted, and hostname checking is off because the Simutronics CA
 * certificate carries no subject alternative name for the game host.
 */
private val logger = Logger.withTag("SocketHelpers")

private const val RECEIVE_MAX = 65536u
private const val WRITE_BUFFER = 8192

actual suspend fun openPlainSocket(
    selectorManager: SelectorManager,
    host: String,
    port: Int,
    coroutineContext: CoroutineContext,
): TLSSocketConnection = openNetworkSocket(host, port, coroutineContext, useTls = false)

actual suspend fun openDefaultTlsSocket(
    selectorManager: SelectorManager,
    host: String,
    port: Int,
    coroutineContext: CoroutineContext,
): TLSSocketConnection =
    // No TLS configuration of our own, so the system evaluates the chain against its own trust
    // store -- which is what the MUD Mobile edge needs; it presents an ordinary public certificate.
    openNetworkSocket(host, port, coroutineContext, useTls = true)

actual suspend fun openTLSSocket(
    selectorManager: SelectorManager,
    host: String,
    port: Int,
    certificate: ByteArray,
    coroutineContext: CoroutineContext,
): TLSSocketConnection {
    val verifyQueue = dispatch_queue_create("warlockfe.warlock3.socket.verify", null)
    return openNetworkSocket(host, port, coroutineContext, useTls = true) { options ->
        val secOptions =
            checkNotNull(nw_tls_copy_sec_protocol_options(options)) {
                "nw_tls_copy_sec_protocol_options failed; refusing to connect without pinning"
            }
        sec_protocol_options_set_verify_block(
            secOptions,
            { _, trust, complete -> complete?.invoke(trustsPinnedCertificate(trust, certificate)) },
            verifyQueue,
        )
    }
}

private suspend fun openNetworkSocket(
    host: String,
    port: Int,
    coroutineContext: CoroutineContext,
    useTls: Boolean,
    configureTls: (nw_protocol_options_t) -> Unit = {},
): TLSSocketConnection {
    val queue = dispatch_queue_create("warlockfe.warlock3.socket", null)

    // The protocol stack is assembled by hand rather than with nw_parameters_create_secure_tcp.
    // That helper selects behaviour by comparing its block arguments against the
    // NW_PARAMETERS_DISABLE_PROTOCOL / NW_PARAMETERS_DEFAULT_CONFIGURATION sentinels by pointer
    // identity, and Kotlin/Native wraps a block on its way across the boundary, so the comparison
    // never matches. The framework then calls those sentinels as if they were ordinary
    // configuration blocks, which left plain sockets attempting TLS and TLS sockets failing the
    // handshake with "bad certificate format". Building the stack explicitly has no such ambiguity.
    val parameters = nw_parameters_create()
    val stack = nw_parameters_copy_default_protocol_stack(parameters)
    nw_protocol_stack_set_transport_protocol(stack, nw_tcp_create_options())
    if (useTls) {
        // checkNotNull, not a null-safe skip: carrying on without these handles would mean no
        // server name and, on the pinned path, no verify block -- a connection that looks fine
        // while doing none of the checking it was asked for. Failing to connect is the safe
        // outcome.
        val tlsOptions = checkNotNull(nw_tls_create_options()) { "nw_tls_create_options failed" }
        val secOptions =
            checkNotNull(nw_tls_copy_sec_protocol_options(tlsOptions)) {
                "nw_tls_copy_sec_protocol_options failed"
            }
        // A hand-built stack gets no SNI: nw_parameters_create_secure_tcp derives the server name
        // from the endpoint, but nothing does that here, so it has to be set explicitly.
        sec_protocol_options_set_tls_server_name(secOptions, host)
        configureTls(tlsOptions)
        nw_protocol_stack_prepend_application_protocol(stack, tlsOptions)
    }

    val endpoint = nw_endpoint_create_host(host, port.toString())
    val connection =
        checkNotNull(nw_connection_create(endpoint, parameters)) {
            "nw_connection_create failed for $host:$port"
        }
    nw_connection_set_queue(connection, queue)

    awaitReady(connection, host, port)

    // Parented to the caller's job. `coroutineContext + job` *replaces* whatever Job the context
    // carried, so a parentless SupervisorJob would quietly detach these loops from the caller:
    // cancelling the caller would leave them running and the connection open.
    val ioJob = SupervisorJob(coroutineContext[Job])
    val scope = CoroutineScope(coroutineContext + ioJob)
    val closed = AtomicBoolean(false)

    val readChannel = ByteChannel(autoFlush = true)
    scope.launch {
        try {
            while (isActive) {
                val chunk = receiveChunk(connection) ?: break
                if (chunk.isNotEmpty()) readChannel.writeFully(chunk)
            }
            readChannel.close()
        } catch (_: CancellationException) {
            readChannel.close()
        } catch (t: Throwable) {
            if (closed.load()) {
                // We cancelled the connection; the receive error is only that landing.
                readChannel.close()
            } else {
                // Closing without a cause would reach the caller as a clean disconnect, and a
                // dropped connection would be reported to the user as an ordinary logout.
                logger.d(t) { "read loop for $host:$port failed" }
                readChannel.cancel(t)
            }
        }
    }

    // A real content context rather than NW_CONNECTION_DEFAULT_MESSAGE_CONTEXT: that global is a
    // sentinel, not an Objective-C object, and Kotlin/Native crashes trying to convert it into a
    // Kotlin reference. One context is reused for every send on this connection.
    val sendContext = nw_content_context_create("warlock")

    val writeChannel = ByteChannel(autoFlush = true)
    scope.launch {
        val reader = writeChannel as ByteReadChannel
        val buffer = ByteArray(WRITE_BUFFER)
        try {
            while (isActive && !reader.isClosedForRead) {
                val count = reader.readAvailable(buffer)
                if (count <= 0) continue
                sendChunk(connection, buffer.copyOf(count), queue, sendContext)
            }
            writeChannel.close()
        } catch (_: CancellationException) {
            writeChannel.close()
        } catch (t: Throwable) {
            if (closed.load()) {
                writeChannel.close()
            } else {
                // The producer keeps writing into a channel nobody drains and eventually suspends
                // forever. Cancelling with the cause turns a silent hang into a reported failure.
                logger.d(t) { "write loop for $host:$port failed" }
                writeChannel.cancel(t)
            }
        }
    }

    // Atomic: close() is reachable from more than one thread, and cancelling an nw_connection_t
    // twice is not something to rely on being harmless.
    fun closeConnection() {
        if (closed.compareAndSet(false, true)) {
            // Cancelling the connection makes any outstanding receive and send complete with an
            // error, which ends both loops. There is nothing to release by hand afterwards.
            nw_connection_cancel(connection)
            ioJob.cancel()
        }
    }

    // Cancelling the caller stops the loops but would otherwise leave the connection itself open,
    // since only close() cancels it. Completion covers both routes: an explicit close (where this
    // is already a no-op) and the caller's job being cancelled out from under us.
    ioJob.invokeOnCompletion { closeConnection() }

    return TLSSocketConnection(
        readChannel = readChannel,
        writeChannel = writeChannel,
        close = { closeConnection() },
    )
}

/** Starts the connection and suspends until it is usable, or fails. */
private suspend fun awaitReady(
    connection: nw_connection_t,
    host: String,
    port: Int,
) {
    suspendCancellableCoroutine { continuation ->
        nw_connection_set_state_changed_handler(connection) { state, error ->
            when (state) {
                nw_connection_state_ready -> {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                nw_connection_state_failed -> {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException("connection to $host:$port failed${error.describe()}"),
                        )
                    }
                }

                nw_connection_state_cancelled -> {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            CancellationException("connection to $host:$port was cancelled"),
                        )
                    }
                }

                else -> {
                    // waiting/preparing/invalid: nothing to do until one of the states above.
                }
            }
        }
        continuation.invokeOnCancellation { nw_connection_cancel(connection) }
        nw_connection_start(connection)
    }

    // Past this point a state change is no longer something to resume; the IO loops surface
    // failures by completing with an error.
    nw_connection_set_state_changed_handler(connection) { state, error ->
        if (state == nw_connection_state_failed) {
            logger.d { "connection to $host:$port failed${error.describe()}" }
        }
    }
}

/** One receive. Returns null at end of stream or on error, which ends the read loop. */
private suspend fun receiveChunk(connection: nw_connection_t): ByteArray? =
    suspendCancellableCoroutine { continuation ->
        nw_connection_receive(connection, 1u, RECEIVE_MAX) { content, _, isComplete, error ->
            if (continuation.isActive) {
                when {
                    error != null -> {
                        logger.d { "receive failed${error.describe()}" }
                        continuation.resumeWithException(
                            IllegalStateException("receive failed${error.describe()}"),
                        )
                    }

                    content != null -> {
                        continuation.resume(content.toByteArray())
                    }

                    isComplete -> {
                        continuation.resume(null)
                    }

                    else -> {
                        continuation.resume(ByteArray(0))
                    }
                }
            }
        }
    }

private suspend fun sendChunk(
    connection: nw_connection_t,
    bytes: ByteArray,
    queue: dispatch_queue_t,
    context: nw_content_context_t,
) {
    suspendCancellableCoroutine { continuation ->
        nw_connection_send(
            connection,
            bytes.toDispatchData(queue),
            context,
            true,
            { error ->
                if (continuation.isActive) {
                    if (error != null) {
                        continuation.resumeWithException(
                            IllegalStateException("send failed${error.describe()}"),
                        )
                    } else {
                        continuation.resume(Unit)
                    }
                }
            },
        )
    }
}

private fun nw_error_t.describe(): String = if (this == null) "" else " (code=${nw_error_get_error_code(this)})"

private fun ByteArray.toDispatchData(queue: dispatch_queue_t): dispatch_data_t =
    usePinned { pinned ->
        // A null destructor means DISPATCH_DATA_DESTRUCTOR_DEFAULT, which copies the buffer, so the
        // pinned array does not have to outlive this call.
        dispatch_data_create(pinned.addressOf(0), size.convert(), queue, null)
    }

private fun dispatch_data_t.toByteArray(): ByteArray =
    memScoped {
        val bufferPtr = alloc<COpaquePointerVar>()
        val sizePtr = alloc<size_tVar>()
        // A dispatch_data_t may be a rope of discontiguous regions; create_map flattens it and
        // returns an object that *owns* the contiguous buffer bufferPtr points at.
        val mapped = dispatch_data_create_map(this@toByteArray, bufferPtr.ptr, sizePtr.ptr)
        val size = sizePtr.value.toInt()
        val bytes =
            if (size == 0) {
                ByteArray(0)
            } else {
                ByteArray(size).also { out ->
                    out.usePinned { pinned ->
                        memcpy(pinned.addressOf(0), bufferPtr.value, sizePtr.value)
                    }
                }
            }
        // `mapped` has to outlive the copy above. Reading it here is what keeps it reachable --
        // left unused, the runtime is free to collect it mid-memcpy and the buffer goes with it.
        check(mapped != null || size == 0) { "dispatch_data_create_map returned null for $size bytes" }
        bytes
    }

/**
 * Trusts the chain if it validates against the pinned certificate as an additional anchor. Keeps
 * the semantics the SecureTransport implementation had: system anchors still count, and the policy
 * is plain X.509 with no hostname check, because the pinned CA certificate has no SAN for the game
 * host.
 */
private fun trustsPinnedCertificate(
    trust: sec_trust_t,
    certificate: ByteArray,
): Boolean {
    val trustRef = sec_trust_copy_ref(trust) ?: return false
    var cert: SecCertificateRef? = null
    var policy: SecPolicyRef? = null
    var certArray: CFArrayRef? = null
    var policyArray: CFArrayRef? = null
    return try {
        memScoped {
            val der =
                if (certificate.decodeToString().contains("-----BEGIN")) {
                    pemToDer(certificate)
                } else {
                    certificate
                }
            val cfData =
                der.usePinned { pinned ->
                    CFDataCreate(null, pinned.addressOf(0).reinterpret(), der.size.convert())
                } ?: return@memScoped false
            val certValue = SecCertificateCreateWithData(null, cfData)
            CFRelease(cfData)
            if (certValue == null) return@memScoped false
            cert = certValue

            val policyValue = SecPolicyCreateBasicX509() ?: return@memScoped false
            policy = policyValue

            certArray =
                CFArrayCreate(
                    null,
                    allocArrayOf(certValue as COpaquePointer).reinterpret(),
                    1,
                    kCFTypeArrayCallBacks.ptr,
                )
            policyArray =
                CFArrayCreate(
                    null,
                    allocArrayOf(policyValue as COpaquePointer).reinterpret(),
                    1,
                    kCFTypeArrayCallBacks.ptr,
                )

            // Unchecked, a failing setter leaves SecTrustEvaluateWithError judging the chain
            // against the default configuration, which can return true for a certificate that
            // does not chain to the pinned one. Pinning would fail open, silently.
            if (SecTrustSetAnchorCertificates(trustRef, certArray) != errSecSuccess ||
                SecTrustSetAnchorCertificatesOnly(trustRef, false) != errSecSuccess ||
                SecTrustSetPolicies(trustRef, policyArray) != errSecSuccess
            ) {
                logger.d { "could not configure pinned trust evaluation" }
                return@memScoped false
            }

            val errorPtr = alloc<CFErrorRefVar>()
            val trusted = SecTrustEvaluateWithError(trustRef, errorPtr.ptr)
            errorPtr.value?.let { CFRelease(it) }
            trusted
        }
    } finally {
        policyArray?.let { CFRelease(it) }
        certArray?.let { CFRelease(it) }
        policy?.let { CFRelease(it) }
        cert?.let { CFRelease(it) }
        CFRelease(trustRef)
    }
}

private fun pemToDer(pem: ByteArray): ByteArray {
    val base64 =
        pem
            .decodeToString()
            .lineSequence()
            .filter { !it.startsWith("-----") }
            .joinToString("")

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    return kotlin.io.encoding.Base64
        .decode(base64)
}
