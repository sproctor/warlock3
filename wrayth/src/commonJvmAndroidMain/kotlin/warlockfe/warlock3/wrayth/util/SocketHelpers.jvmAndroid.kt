package warlockfe.warlock3.wrayth.util

import io.ktor.network.tls.TLSConfigBuilder
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

actual fun TLSConfigBuilder.configureTLS(certificate: ByteArray) {
    // Add certificate to key store
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
    keyStore.load(null)
    val certFactory = CertificateFactory.getInstance("X.509")
    val cert = certFactory.generateCertificate(certificate.inputStream())
    keyStore.setCertificateEntry("ca", cert)
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    trustManagerFactory.init(keyStore)
    trustManager = trustManagerFactory.trustManagers.first { it is X509TrustManager }
}