package warlockfe.warlock3.wrayth.util

import org.apache.commons.text.StringEscapeUtils

actual fun unescapeXml(text: String): String {
    return StringEscapeUtils.unescapeXml(text)
}
