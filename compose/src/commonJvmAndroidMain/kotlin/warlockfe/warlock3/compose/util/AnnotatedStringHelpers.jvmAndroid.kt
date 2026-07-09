package warlockfe.warlock3.compose.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import org.nibor.autolink.LinkExtractor
import org.nibor.autolink.LinkType
import warlockfe.warlock3.core.text.StyleLayer
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.core.text.resolve

actual fun AnnotatedString.Builder.markLinks(
    text: AnnotatedString,
    presets: Map<String, StyleLayer>,
) {
    linkExtractor.extractLinks(text.text).forEach { link ->
        if (text.getLinkAnnotations(link.beginIndex, link.endIndex).isEmpty()) {
            addStyle(
                // Links use the "link" preset, which is never monospace, so no monospace font is needed.
                style = resolve(listOf(WarlockStyle("link").toStyleLayer(presets))).toSpanStyle(null),
                start = link.beginIndex,
                end = link.endIndex,
            )
            val substring = text.substring(link.beginIndex, link.endIndex)
            addLink(
                url =
                    LinkAnnotation.Url(
                        if (link.type == LinkType.URL) {
                            substring
                        } else {
                            "http://$substring"
                        },
                    ),
                start = link.beginIndex,
                end = link.endIndex,
            )
        }
    }
}

private val linkExtractor = LinkExtractor.builder().linkTypes(setOf(LinkType.URL, LinkType.WWW)).build()

actual val MatchGroup.range_: IntRange
    get() = this.range
