package warlockfe.warlock3.wrayth.util

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.tls.tls
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.CoroutineContext

actual suspend fun openPlainSocket(
    selectorManager: SelectorManager,
    host: String,
    port: Int,
    coroutineContext: CoroutineContext,
): TLSSocketConnection {
    val socket =
        aSocket(selectorManager)
            .tcp()
            .connect(host, port)
    return TLSSocketConnection(
        readChannel = socket.openReadChannel(),
        writeChannel = socket.openWriteChannel(autoFlush = true),
        close = { socket.close() },
    )
}

actual suspend fun openTLSSocket(
    selectorManager: SelectorManager,
    host: String,
    port: Int,
    certificate: ByteArray,
    coroutineContext: CoroutineContext,
): TLSSocketConnection {
    val socket =
        aSocket(selectorManager)
            .tcp()
            .connect(host, port)
            .tls(coroutineContext = coroutineContext) {
                val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
                keyStore.load(null)
                val certFactory = CertificateFactory.getInstance("X.509")
                val cert = certFactory.generateCertificate(certificate.inputStream())
                keyStore.setCertificateEntry("ca", cert)
                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                tmf.init(keyStore)
                trustManager = tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
            }
    return TLSSocketConnection(
        readChannel = socket.openReadChannel(),
        writeChannel = socket.openWriteChannel(autoFlush = true),
        close = { socket.close() },
    )
}
