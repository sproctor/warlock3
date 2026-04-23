package warlockfe.warlock3.wrayth.util

actual fun unescapeXml(text: String): String {
    if ('&' !in text) return text
    val result = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c != '&') {
            result.append(c)
            i++
            continue
        }
        val semi = text.indexOf(';', startIndex = i + 1)
        if (semi < 0) {
            result.append(c)
            i++
            continue
        }
        val entity = text.substring(i + 1, semi)
        val consumed = when {
            entity == "amp" -> result.append('&').let { true }
            entity == "lt" -> result.append('<').let { true }
            entity == "gt" -> result.append('>').let { true }
            entity == "quot" -> result.append('"').let { true }
            entity == "apos" -> result.append('\'').let { true }
            entity.startsWith("#x") || entity.startsWith("#X") ->
                entity.substring(2).toIntOrNull(16)?.let { result.appendCodePoint(it); true } == true

            entity.startsWith("#") ->
                entity.substring(1).toIntOrNull()?.let { result.appendCodePoint(it); true } == true

            else -> false
        }
        if (consumed) {
            i = semi + 1
        } else {
            result.append(c)
            i++
        }
    }
    return result.toString()
}

private fun StringBuilder.appendCodePoint(codePoint: Int) {
    when {
        codePoint !in 0..0x10FFFF -> Unit
        codePoint <= 0xFFFF -> append(codePoint.toChar())
        else -> {
            val adjusted = codePoint - 0x10000
            append(((adjusted ushr 10) + 0xD800).toChar())
            append(((adjusted and 0x3FF) + 0xDC00).toChar())
        }
    }
}
