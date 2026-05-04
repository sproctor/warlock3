package warlockfe.warlock3.wrayth.util

fun unescapeXml(text: String): String {
    if ('&' !in text) return text
    val sb = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c != '&') {
            sb.append(c)
            i++
            continue
        }
        val end = text.indexOf(';', i + 1)
        if (end == -1) {
            sb.append(c)
            i++
            continue
        }
        val entity = text.substring(i + 1, end)
        val replacement =
            when {
                entity == "amp" -> "&"
                entity == "lt" -> "<"
                entity == "gt" -> ">"
                entity == "quot" -> "\""
                entity == "apos" -> "'"
                entity.startsWith("#x") || entity.startsWith("#X") ->
                    entity.substring(2).toIntOrNull(16)?.let { codePointToString(it) }
                entity.startsWith("#") ->
                    entity.substring(1).toIntOrNull()?.let { codePointToString(it) }
                else -> null
            }
        if (replacement != null) {
            sb.append(replacement)
            i = end + 1
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}

private fun codePointToString(cp: Int): String? {
    if (cp !in 0..0x10FFFF) return null
    return if (cp <= 0xFFFF) {
        Char(cp).toString()
    } else {
        val adjusted = cp - 0x10000
        val high = 0xD800 + (adjusted ushr 10)
        val low = 0xDC00 + (adjusted and 0x3FF)
        charArrayOf(Char(high), Char(low)).concatToString()
    }
}
