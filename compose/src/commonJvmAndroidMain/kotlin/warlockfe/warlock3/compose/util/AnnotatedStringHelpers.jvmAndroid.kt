package warlockfe.warlock3.compose.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import org.nibor.autolink.LinkExtractor
import org.nibor.autolink.LinkType
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.WarlockStyle

actual fun AnnotatedString.Builder.markLinks(
    highlightedResult: AnnotatedStringHighlightResult,
    presets: Map<String, StyleDefinition>
) {
    linkExtractor.extractLinks(highlightedResult.text.text).forEach { link ->
        if (highlightedResult.text.getLinkAnnotations(link.beginIndex, link.endIndex).isEmpty()) {
            addStyle(
                style = WarlockStyle("link").toStyleDefinition(presets).toSpanStyle(),
                start = link.beginIndex,
                end = link.endIndex,
            )
            val substring = highlightedResult.text.substring(link.beginIndex, link.endIndex)
            addLink(
                url = LinkAnnotation.Url(
                    if (link.type == LinkType.URL) {
                        substring
                    } else {
                        "http://$substring"
                    }
                ),
                start = link.beginIndex,
                end = link.endIndex,
            )
        }
    }
}

private val linkExtractor = LinkExtractor.builder().linkTypes(setOf(LinkType.URL, LinkType.WWW)).build()