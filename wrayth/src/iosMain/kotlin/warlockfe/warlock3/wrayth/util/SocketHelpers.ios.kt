package warlockfe.warlock3.wrayth.util

import io.ktor.network.tls.TLSConfigBuilder

actual fun TLSConfigBuilder.configureTLS(certificate: ByteArray) {
    // On iOS, ktor-network-tls uses the platform's native TLS.
    // For now we trust the system's default certificate store.
    // The SGE server certificate is signed by a well-known CA,
    // so no custom trust configuration is needed on iOS.
}
