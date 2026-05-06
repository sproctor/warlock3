package warlockfe.warlock3.core.util

import java.nio.charset.Charset

private val windows1252Charset = Charset.forName("windows-1252")

actual fun ByteArray.decodeWindows1252(
    offset: Int,
    length: Int,
): String = String(this, offset, length, windows1252Charset)

actual fun String.encodeWindows1252(): ByteArray = toByteArray(windows1252Charset)
