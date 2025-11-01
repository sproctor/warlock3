package warlockfe.warlock3.core.util

import java.nio.charset.Charset

private val windows1252Charset = Charset.forName("windows-1252")

actual fun ByteArray.decodeWindows1252(
    offset: Int,
    length: Int,
): String {
    return String(this, offset, length, windows1252Charset)
}

actual fun String.encodeWindows1252(): ByteArray {
    return toByteArray(windows1252Charset)
}
