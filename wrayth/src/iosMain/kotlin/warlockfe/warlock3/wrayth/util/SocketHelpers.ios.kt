@file:Suppress("DEPRECATION")
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package warlockfe.warlock3.wrayth.util

import io.ktor.network.selector.SelectorManager
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.CoreFoundation.CFArrayCreate
import platform.CoreFoundation.CFArrayRef
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFTypeArrayCallBacks
import platform.Security.SSLClose
import platform.Security.SSLConnectionRef
import platform.Security.SSLConnectionType
import platform.Security.SSLContextRef
import platform.Security.SSLCopyPeerTrust
import platform.Security.SSLCreateContext
import platform.Security.SSLHandshake
import platform.Security.SSLProtocolSide
import platform.Security.SSLRead
import platform.Security.SSLSetConnection
import platform.Security.SSLSetIOFuncs
import platform.Security.SSLSetPeerDomainName
import platform.Security.SSLSetSessionOption
import platform.Security.SSLWrite
import platform.Security.SecCertificateCreateWithData
import platform.Security.SecCertificateRef
import platform.Security.SecPolicyCreateBasicX509
import platform.Security.SecPolicyRef
import platform.Security.SecTrustEvaluateWithError
import platform.Security.SecTrustRefVar
import platform.Security.SecTrustSetAnchorCertificates
import platform.Security.SecTrustSetAnchorCertificatesOnly
import platform.Security.SecTrustSetPolicies
import platform.Security.errSSLClosedAbort
import platform.Security.errSSLClosedGraceful
import platform.Security.errSSLPeerAuthCompleted
import platform.Security.errSSLWouldBlock
import platform.Security.errSecSuccess
import platform.Security.kSSLSessionOptionBreakOnServerAuth
import platform.posix.AF_INET
import platform.posix.EAGAIN
import platform.posix.EWOULDBLOCK
import platform.posix.IPPROTO_TCP
import platform.posix.SOCK_STREAM
import platform.posix.addrinfo
import platform.posix.close
import platform.posix.connect
import platform.posix.errno
import platform.posix.freeaddrinfo
import platform.posix.getaddrinfo
import platform.posix.read
import platform.posix.socket
import platform.posix.write
import kotlin.coroutines.CoroutineContext

// SSL I/O callbacks are static (no captures); socket fd is stored via SSLSetConnection as a stable ref.

private val sslReadCallback =
    staticCFunction {
        connection: SSLConnectionRef?,
        data: COpaquePointer?,
        dataLength: CPointer<ULongVar>?,
        ->

        if (connection == null || data == null || dataLength == null) return@staticCFunction errSSLClosedAbort
        val fdArr = connection.asStableRef<IntArray>().get()
        val fd = fdArr[0]
        val n = read(fd, data, dataLength.pointed.value)
        when {
            n > 0L -> {
                dataLength.pointed.value = n.convert()
                errSecSuccess
            }

            n == 0L -> {
                dataLength.pointed.value = 0u
                errSSLClosedGraceful
            }

            else -> {
                dataLength.pointed.value = 0u
                if (errno == EAGAIN || errno == EWOULDBLOCK) errSSLWouldBlock else errSSLClosedAbort
            }
        }
    }

private val sslWriteCallback =
    staticCFunction {
        connection: SSLConnectionRef?,
        data: COpaquePointer?,
        dataLength: CPointer<ULongVar>?,
        ->

        if (connection == null || data == null || dataLength == null) return@staticCFunction errSSLClosedAbort
        val fdArr = connection.asStableRef<IntArray>().get()
        val fd = fdArr[0]
        val n = write(fd, data, dataLength.pointed.value)
        when {
            n >= 0L -> {
                dataLength.pointed.value = n.convert()
                errSecSuccess
            }

            else -> {
                dataLength.pointed.value = 0u
                if (errno == EAGAIN || errno == EWOULDBLOCK) errSSLWouldBlock else errSSLClosedAbort
            }
        }
    }

private fun createAndConnectSocket(
    host: String,
    port: Int,
): Int =
    memScoped {
        val hints =
            alloc<addrinfo>().apply {
                ai_family = AF_INET
                ai_socktype = SOCK_STREAM
                ai_protocol = IPPROTO_TCP
            }
        val resultPtr = alloc<CPointerVar<addrinfo>>()
        val rc = getaddrinfo(host, port.toString(), hints.ptr, resultPtr.ptr)
        check(rc == 0) { "getaddrinfo failed for $host:$port (rc=$rc)" }
        val result = checkNotNull(resultPtr.value) { "getaddrinfo returned null for $host:$port" }
        val fd = socket(result.pointed.ai_family, result.pointed.ai_socktype, result.pointed.ai_protocol)
        check(fd >= 0) { "socket() failed" }
        val connected = connect(fd, result.pointed.ai_addr, result.pointed.ai_addrlen)
        freeaddrinfo(result)
        if (connected != 0) {
            // The descriptor exists even though connect() failed; without this every failed
            // connection attempt burns one until the process runs out.
            close(fd)
            error("connect() failed to $host:$port")
        }
        fd
    }

private fun setupTLS(
    fd: Int,
    host: String,
    certificate: ByteArray,
): Pair<SSLContextRef, StableRef<IntArray>> {
    val sslCtx =
        checkNotNull(
            SSLCreateContext(null, SSLProtocolSide.kSSLClientSide, SSLConnectionType.kSSLStreamType),
        ) {
            "SSLCreateContext failed"
        }
    val fdHolder = intArrayOf(fd)
    val stableRef = StableRef.create(fdHolder)

    // Anything thrown below (a failed handshake is routine) must not strand the
    // SecureTransport context or the stable ref the IO callbacks read the fd from.
    try {
        SSLSetConnection(sslCtx, stableRef.asCPointer())
        SSLSetIOFuncs(sslCtx, sslReadCallback, sslWriteCallback)
        SSLSetPeerDomainName(sslCtx, host, host.length.convert())

        // Break on server auth so we can evaluate trust with our custom certificate
        SSLSetSessionOption(sslCtx, kSSLSessionOptionBreakOnServerAuth, true)

        val firstStatus = SSLHandshake(sslCtx)
        var status = firstStatus
        if (status == errSSLPeerAuthCompleted) {
            // Evaluate server trust with our custom CA certificate
            evaluateServerTrust(sslCtx, certificate)
            // Continue handshake after trust evaluation
            status = SSLHandshake(sslCtx)
        }
        // Report both statuses: -50 (errSecParam) on the *first* call means SecureTransport rejected the
        // context setup, while -50 only on the second means the trust evaluation left it unusable. The
        // two have completely different fixes, so never collapse them into one number.
        check(status == errSecSuccess) {
            "TLS handshake failed for $host (first=$firstStatus, final=$status)"
        }
    } catch (t: Throwable) {
        CFRelease(sslCtx)
        stableRef.dispose()
        throw t
    }

    return sslCtx to stableRef
}

private fun pemToDer(pem: ByteArray): ByteArray {
    val pemString = pem.decodeToString()
    val base64 =
        pemString
            .lineSequence()
            .filter { !it.startsWith("-----") }
            .joinToString("")
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    return kotlin.io.encoding.Base64
        .decode(base64)
}

private fun evaluateServerTrust(
    sslCtx: SSLContextRef,
    certificate: ByteArray,
) = memScoped {
    val trustPtr = alloc<SecTrustRefVar>()
    SSLCopyPeerTrust(sslCtx, trustPtr.ptr)
    val trust = checkNotNull(trustPtr.value) { "SSLCopyPeerTrust returned null" }

    // Everything below is acquired inside the try and released in the finally, in reverse order and
    // only if it was actually acquired. A checkNotNull between two allocations would otherwise leak
    // `trust` and whatever else had already been created.
    var cert: SecCertificateRef? = null
    var policy: SecPolicyRef? = null
    var certCFArray: CFArrayRef? = null
    var policyArray: CFArrayRef? = null

    val trusted =
        try {
            // Convert PEM to DER if needed, then create SecCertificate
            val derData =
                if (certificate.decodeToString().contains("-----BEGIN")) {
                    pemToDer(certificate)
                } else {
                    certificate
                }
            val cfData =
                checkNotNull(
                    derData.usePinned { pinned ->
                        CFDataCreate(null, pinned.addressOf(0).reinterpret(), derData.size.convert())
                    },
                ) { "CFDataCreate failed" }
            val certRef = SecCertificateCreateWithData(null, cfData)
            CFRelease(cfData)
            val certValue = checkNotNull(certRef) { "SecCertificateCreateWithData failed" }
            cert = certValue

            val policyValue =
                checkNotNull(SecPolicyCreateBasicX509()) { "SecPolicyCreateBasicX509 failed" }
            policy = policyValue

            // kCFTypeArrayCallBacks rather than null: an array built with null callbacks does not
            // retain what it holds, so releasing `cert` and `policy` below would leave `trust`
            // referencing freed memory. That is a use-after-free which only misbehaves when the
            // allocator happens to reuse the block -- precisely the kind of fault that appears to
            // come and go between runs.
            certCFArray =
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

            // Set as anchor certificate, but also allow system anchors
            SecTrustSetAnchorCertificates(trust, certCFArray)
            SecTrustSetAnchorCertificatesOnly(trust, false)

            // Replace the SSL policy with a basic X.509 policy (no hostname check).
            // Hostname verification is already handled by SSLSetPeerDomainName at the SSL layer;
            // the self-signed CA cert doesn't have a SAN for the server hostname.
            SecTrustSetPolicies(trust, policyArray)

            val errorPtr = alloc<CFErrorRefVar>()
            val result = SecTrustEvaluateWithError(trust, errorPtr.ptr)
            errorPtr.value?.let { CFRelease(it) }
            result
        } finally {
            policyArray?.let { CFRelease(it) }
            certCFArray?.let { CFRelease(it) }
            policy?.let { CFRelease(it) }
            cert?.let { CFRelease(it) }
            CFRelease(trust)
        }

    check(trusted) { "Server certificate trust evaluation failed" }
}

actual suspend fun openPlainSocket(
    selectorManager: SelectorManager,
    host: String,
    port: Int,
    coroutineContext: CoroutineContext,
): TLSSocketConnection {
    val fd =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            createAndConnectSocket(host, port)
        }

    val scope = CoroutineScope(coroutineContext + SupervisorJob())

    val readChannel = ByteChannel(autoFlush = true)
    scope.launch(Dispatchers.IO) {
        val buf = ByteArray(8192)
        try {
            while (isActive) {
                val n =
                    buf.usePinned { pinned ->
                        read(fd, pinned.addressOf(0), buf.size.convert())
                    }
                if (n <= 0) break
                readChannel.writeFully(buf, 0, n.toInt())
            }
        } finally {
            readChannel.close()
        }
    }

    val writeChannel = ByteChannel(autoFlush = true)
    scope.launch(Dispatchers.IO) {
        val reader = writeChannel as ByteReadChannel
        val buf = ByteArray(8192)
        try {
            while (isActive && !reader.isClosedForRead) {
                val n = reader.readAvailable(buf)
                if (n <= 0) continue
                buf.usePinned { pinned ->
                    write(fd, pinned.addressOf(0), n.convert())
                }
            }
        } catch (_: Exception) {
        }
    }

    var closed = false
    return TLSSocketConnection(
        readChannel = readChannel,
        writeChannel = writeChannel,
        close = {
            if (!closed) {
                closed = true
                scope.cancel()
                close(fd)
            }
        },
    )
}

private fun setupDefaultTLS(
    fd: Int,
    host: String,
): Pair<SSLContextRef, StableRef<IntArray>> {
    val sslCtx =
        checkNotNull(
            SSLCreateContext(null, SSLProtocolSide.kSSLClientSide, SSLConnectionType.kSSLStreamType),
        ) {
            "SSLCreateContext failed"
        }
    val fdHolder = intArrayOf(fd)
    val stableRef = StableRef.create(fdHolder)

    // Anything thrown below (a failed handshake is routine) must not strand the
    // SecureTransport context or the stable ref the IO callbacks read the fd from.
    try {
        SSLSetConnection(sslCtx, stableRef.asCPointer())
        SSLSetIOFuncs(sslCtx, sslReadCallback, sslWriteCallback)
        SSLSetPeerDomainName(sslCtx, host, host.length.convert())

        // No breakOnServerAuth: SecureTransport performs default system trust evaluation during the
        // handshake, which is exactly what we want for a public CA-signed certificate.
        val status = SSLHandshake(sslCtx)
        check(status == errSecSuccess) { "TLS handshake failed (status=$status)" }
    } catch (t: Throwable) {
        CFRelease(sslCtx)
        stableRef.dispose()
        throw t
    }

    return sslCtx to stableRef
}

actual suspend fun openDefaultTlsSocket(
    selectorManager: SelectorManager,
    host: String,
    port: Int,
    coroutineContext: CoroutineContext,
): TLSSocketConnection {
    val (fd, sslCtx, stableRef) =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val fd = createAndConnectSocket(host, port)
            val (ctx, ref) =
                try {
                    setupDefaultTLS(fd, host)
                } catch (t: Throwable) {
                    close(fd)
                    throw t
                }
            Triple(fd, ctx, ref)
        }

    val scope = CoroutineScope(coroutineContext + SupervisorJob())

    val readChannel = ByteChannel(autoFlush = true)
    scope.launch(Dispatchers.IO) {
        val ubuf = UByteArray(8192)
        val processedRef = nativeHeap.alloc<ULongVar>()
        try {
            while (isActive) {
                var n = 0
                ubuf.usePinned { pinned ->
                    processedRef.value = 0u
                    val status = SSLRead(sslCtx, pinned.addressOf(0), 8192u, processedRef.ptr)
                    n =
                        if (status == errSecSuccess || status == errSSLWouldBlock) {
                            processedRef.value.toInt()
                        } else {
                            -1
                        }
                }
                if (n <= 0) break
                readChannel.writeFully(ByteArray(n) { ubuf[it].toByte() })
            }
        } finally {
            nativeHeap.free(processedRef.rawPtr)
            readChannel.close()
        }
    }

    val writeChannel = ByteChannel(autoFlush = true)
    scope.launch(Dispatchers.IO) {
        val reader = writeChannel as ByteReadChannel
        val buf = ByteArray(8192)
        val processedRef = nativeHeap.alloc<ULongVar>()
        try {
            while (isActive && !reader.isClosedForRead) {
                val n = reader.readAvailable(buf)
                if (n <= 0) continue
                val ubuf2 = UByteArray(n) { buf[it].toUByte() }
                ubuf2.usePinned { pinned ->
                    processedRef.value = 0u
                    SSLWrite(sslCtx, pinned.addressOf(0), n.convert(), processedRef.ptr)
                }
            }
        } catch (_: Exception) {
        } finally {
            nativeHeap.free(processedRef.rawPtr)
        }
    }

    var closed = false
    return TLSSocketConnection(
        readChannel = readChannel,
        writeChannel = writeChannel,
        close = {
            if (!closed) {
                closed = true
                scope.cancel()
                SSLClose(sslCtx)
                close(fd)
                // SSLCreateContext returns a +1 reference; SSLClose does not consume it.
                CFRelease(sslCtx)
                stableRef.dispose()
            }
        },
    )
}

actual suspend fun openTLSSocket(
    selectorManager: SelectorManager,
    host: String,
    port: Int,
    certificate: ByteArray,
    coroutineContext: CoroutineContext,
): TLSSocketConnection {
    val (fd, sslCtx, stableRef) =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val fd = createAndConnectSocket(host, port)
            val (ctx, ref) =
                try {
                    setupTLS(fd, host, certificate)
                } catch (t: Throwable) {
                    close(fd)
                    throw t
                }
            Triple(fd, ctx, ref)
        }

    val scope = CoroutineScope(coroutineContext + SupervisorJob())

    // SSLRead → ByteChannel
    val readChannel = ByteChannel(autoFlush = true)
    scope.launch(Dispatchers.IO) {
        val ubuf = UByteArray(8192)
        val processedRef = nativeHeap.alloc<ULongVar>()
        try {
            while (isActive) {
                var n = 0
                ubuf.usePinned { pinned ->
                    processedRef.value = 0u
                    val status = SSLRead(sslCtx, pinned.addressOf(0), 8192u, processedRef.ptr)
                    n =
                        if (status == errSecSuccess || status == errSSLWouldBlock) {
                            processedRef.value.toInt()
                        } else {
                            -1
                        }
                }
                if (n <= 0) break
                readChannel.writeFully(ByteArray(n) { ubuf[it].toByte() })
            }
        } finally {
            nativeHeap.free(processedRef.rawPtr)
            readChannel.close()
        }
    }

    // ByteChannel → SSLWrite
    val writeChannel = ByteChannel(autoFlush = true)
    scope.launch(Dispatchers.IO) {
        val reader = writeChannel as ByteReadChannel
        val buf = ByteArray(8192)
        val processedRef = nativeHeap.alloc<ULongVar>()
        try {
            while (isActive && !reader.isClosedForRead) {
                val n = reader.readAvailable(buf)
                if (n <= 0) continue
                val ubuf2 = UByteArray(n) { buf[it].toUByte() }
                ubuf2.usePinned { pinned ->
                    processedRef.value = 0u
                    SSLWrite(sslCtx, pinned.addressOf(0), n.convert(), processedRef.ptr)
                }
            }
        } catch (_: Exception) {
        } finally {
            nativeHeap.free(processedRef.rawPtr)
        }
    }

    var closed = false
    return TLSSocketConnection(
        readChannel = readChannel,
        writeChannel = writeChannel,
        close = {
            if (!closed) {
                closed = true
                scope.cancel()
                SSLClose(sslCtx)
                close(fd)
                // SSLCreateContext returns a +1 reference; SSLClose does not consume it.
                CFRelease(sslCtx)
                stableRef.dispose()
            }
        },
    )
}
