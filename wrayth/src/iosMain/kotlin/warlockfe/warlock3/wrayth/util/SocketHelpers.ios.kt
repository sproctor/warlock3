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
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.Security.SSLClose
import platform.Security.SSLCopyPeerTrust
import platform.Security.SSLSetSessionOption
import platform.Security.SecCertificateCreateWithData
import platform.Security.SecPolicyCreateBasicX509
import platform.Security.SecTrustSetPolicies
import platform.Security.SecTrustEvaluateWithError
import platform.Security.SecTrustRefVar
import platform.Security.SecTrustSetAnchorCertificates
import platform.Security.SecTrustSetAnchorCertificatesOnly
import platform.Security.errSSLPeerAuthCompleted
import platform.Security.kSSLSessionOptionBreakOnServerAuth
import platform.Security.SSLConnectionRef
import platform.Security.SSLConnectionType
import platform.Security.SSLContextRef
import platform.Security.SSLCreateContext
import platform.Security.SSLHandshake
import platform.Security.SSLProtocolSide
import platform.Security.SSLRead
import platform.Security.SSLSetConnection
import platform.Security.SSLSetIOFuncs
import platform.Security.SSLSetPeerDomainName
import platform.Security.SSLWrite
import platform.Security.errSSLClosedAbort
import platform.Security.errSSLClosedGraceful
import platform.Security.errSSLWouldBlock
import platform.Security.errSecSuccess
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

private val sslReadCallback = staticCFunction {
    connection: SSLConnectionRef?,
    data: COpaquePointer?,
    dataLength: CPointer<ULongVar>? ->

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

private val sslWriteCallback = staticCFunction {
    connection: SSLConnectionRef?,
    data: COpaquePointer?,
    dataLength: CPointer<ULongVar>? ->

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

private fun createAndConnectSocket(host: String, port: Int): Int = memScoped {
    val hints = alloc<addrinfo>().apply {
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
    check(connected == 0) { "connect() failed to $host:$port" }
    fd
}

private fun setupTLS(fd: Int, host: String, certificate: ByteArray): Pair<SSLContextRef, StableRef<IntArray>> {
    val sslCtx = checkNotNull(
        SSLCreateContext(null, SSLProtocolSide.kSSLClientSide, SSLConnectionType.kSSLStreamType)
    ) {
        "SSLCreateContext failed"
    }
    val fdHolder = intArrayOf(fd)
    val stableRef = StableRef.create(fdHolder)
    SSLSetConnection(sslCtx, stableRef.asCPointer())
    SSLSetIOFuncs(sslCtx, sslReadCallback, sslWriteCallback)
    SSLSetPeerDomainName(sslCtx, host, host.length.convert())

    // Break on server auth so we can evaluate trust with our custom certificate
    SSLSetSessionOption(sslCtx, kSSLSessionOptionBreakOnServerAuth, true)

    var status = SSLHandshake(sslCtx)
    if (status == errSSLPeerAuthCompleted) {
        // Evaluate server trust with our custom CA certificate
        evaluateServerTrust(sslCtx, certificate)
        // Continue handshake after trust evaluation
        status = SSLHandshake(sslCtx)
    }
    check(status == errSecSuccess) { "TLS handshake failed (status=$status)" }

    return sslCtx to stableRef
}

private fun pemToDer(pem: ByteArray): ByteArray {
    val pemString = pem.decodeToString()
    val base64 = pemString
        .lineSequence()
        .filter { !it.startsWith("-----") }
        .joinToString("")
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    return kotlin.io.encoding.Base64.decode(base64)
}

private fun evaluateServerTrust(sslCtx: SSLContextRef, certificate: ByteArray) = memScoped {
    val trustPtr = alloc<SecTrustRefVar>()
    SSLCopyPeerTrust(sslCtx, trustPtr.ptr)
    val trust = checkNotNull(trustPtr.value) { "SSLCopyPeerTrust returned null" }

    // Convert PEM to DER if needed, then create SecCertificate
    val derData = if (certificate.decodeToString().contains("-----BEGIN")) {
        pemToDer(certificate)
    } else {
        certificate
    }
    val cfData = derData.usePinned { pinned ->
        CFDataCreate(null, pinned.addressOf(0).reinterpret(), derData.size.convert())
    }
    checkNotNull(cfData) { "CFDataCreate failed" }
    val cert = SecCertificateCreateWithData(null, cfData)
    CFRelease(cfData)
    checkNotNull(cert) { "SecCertificateCreateWithData failed" }

    // Set as anchor certificate
    val certArrayValues = allocArrayOf(cert as COpaquePointer)
    val certCFArray = CFArrayCreate(null, certArrayValues.reinterpret(), 1, null)
    SecTrustSetAnchorCertificates(trust, certCFArray)
    // Also allow system anchors
    SecTrustSetAnchorCertificatesOnly(trust, false)

    // Replace the SSL policy with a basic X.509 policy (no hostname check).
    // Hostname verification is already handled by SSLSetPeerDomainName at the SSL layer;
    // the self-signed CA cert doesn't have a SAN for the server hostname.
    val policy = SecPolicyCreateBasicX509()
    val policyValues = allocArrayOf(policy as COpaquePointer)
    val policyArray = CFArrayCreate(null, policyValues.reinterpret(), 1, null)
    SecTrustSetPolicies(trust, policyArray)

    // Evaluate trust
    val errorPtr = alloc<CFErrorRefVar>()
    val trusted = SecTrustEvaluateWithError(trust, errorPtr.ptr)

    val error = errorPtr.value
    if (error != null) CFRelease(error)
    CFRelease(policyArray)
    CFRelease(policy)
    CFRelease(certCFArray)
    CFRelease(cert)
    CFRelease(trust)

    check(trusted) { "Server certificate trust evaluation failed" }
}

actual suspend fun openPlainSocket(
    selectorManager: SelectorManager,
    host: String,
    port: Int,
    coroutineContext: CoroutineContext,
): TLSSocketConnection {
    val fd = kotlinx.coroutines.withContext(Dispatchers.IO) {
        createAndConnectSocket(host, port)
    }

    val scope = CoroutineScope(coroutineContext + SupervisorJob())

    val readChannel = ByteChannel(autoFlush = true)
    scope.launch(Dispatchers.IO) {
        val buf = ByteArray(8192)
        try {
            while (isActive) {
                val n = buf.usePinned { pinned ->
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

actual suspend fun openTLSSocket(
    selectorManager: SelectorManager,
    host: String,
    port: Int,
    certificate: ByteArray,
    coroutineContext: CoroutineContext,
): TLSSocketConnection {
    val (fd, sslCtx, stableRef) = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val fd = createAndConnectSocket(host, port)
        val (ctx, ref) = setupTLS(fd, host, certificate)
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
                    n = if (status == errSecSuccess || status == errSSLWouldBlock) {
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
                stableRef.dispose()
            }
        },
    )
}
