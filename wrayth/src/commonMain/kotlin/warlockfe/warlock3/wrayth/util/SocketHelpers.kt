package warlockfe.warlock3.wrayth.util

import io.ktor.network.tls.TLSConfigBuilder

expect fun TLSConfigBuilder.configureTLS(certificate: ByteArray): Unit