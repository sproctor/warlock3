package warlockfe.warlock3.compose.util

actual fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> =
    throw UnsupportedOperationException("Zip skins are not supported on iOS")
