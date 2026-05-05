package warlockfe.warlock3.compose.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import warlockfe.warlock3.core.client.WarlockAction
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.text.StyledString
import warlockfe.warlock3.core.text.StyledStringLeaf
import warlockfe.warlock3.core.text.StyledStringSubstring
import warlockfe.warlock3.core.text.StyledStringVariable
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.core.text.flattenStyles

fun StyledString.toAnnotatedString(
    variables: Map<String, StyledString>,
    styleMap: Map<String, StyleDefinition>,
    actionHandler: (WarlockAction) -> Unit,
): AnnotatedString =
    buildAnnotatedString {
        appendStyledString(this@toAnnotatedString, variables, styleMap, actionHandler)
    }

fun StyleDefinition.toSpanStyle(): SpanStyle =
    SpanStyle(
        color = textColor.toColor(),
        background = backgroundColor.toColor(),
        fontFamily = fontFamily?.let { createFontFamily(it) },
        textDecoration = if (underline) TextDecoration.Underline else null,
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        fontSize = fontSize?.sp ?: TextUnit.Unspecified,
    )

fun WarlockStyle.toStyleDefinition(styleMap: Map<String, StyleDefinition>): StyleDefinition = (styleMap[name] ?: StyleDefinition())

private fun AnnotatedString.Builder.appendStyledString(
    styledString: StyledString,
    variables: Map<String, StyledString>,
    styleMap: Map<String, StyleDefinition>,
    actionHandler: (WarlockAction) -> Unit,
) {
    styledString.substrings.forEach {
        appendStyledStringLeaf(it, variables, styleMap, actionHandler)
    }
}

private fun AnnotatedString.Builder.appendStyledStringLeaf(
    leaf: StyledStringLeaf,
    variables: Map<String, StyledString>,
    styleMap: Map<String, StyleDefinition>,
    actionHandler: (WarlockAction) -> Unit,
) {
    val style =
        flattenStyles(leaf.styles.map { it.toStyleDefinition(styleMap) })
            ?.also { pushStyle(it.toSpanStyle()) }

    var linksPushed = 0
    leaf.styles.forEach { st ->
        st.action?.let { action ->
            val link =
                when (action) {
                    is WarlockAction.OpenLink ->
                        LinkAnnotation.Url(action.url)

                    else ->
                        LinkAnnotation.Clickable("action") {
                            actionHandler(action)
                        }
                }
            pushLink(link)
            linksPushed++
        }
    }
    when (leaf) {
        is StyledStringSubstring -> append(leaf.text)
        is StyledStringVariable ->
            // TODO: break circular references
            variables[leaf.name]?.let {
                appendStyledString(it, variables, styleMap, actionHandler)
            }
    }
    if (style != null) {
        pop()
    }
    repeat(linksPushed) { pop() }
}

fun StyledString.getEntireLineStyles(
    variables: Map<String, StyledString>,
    styleMap: Map<String, StyleDefinition>,
): List<StyleDefinition> = substrings.flatMap { substring -> substring.getEntireLineStyles(variables, styleMap) }

fun StyledStringLeaf.getEntireLineStyles(
    variables: Map<String, StyledString>,
    styleMap: Map<String, StyleDefinition>,
): List<StyleDefinition> {
    val entireLineStyles = styles.mapNotNull { styleMap[it.name] }.filter { it.entireLine }
    return when (this) {
        is StyledStringSubstring -> entireLineStyles
        is StyledStringVariable ->
            entireLineStyles + (
                variables[name]?.getEntireLineStyles(variables, styleMap)
                    ?: emptyList()
            )
    }
}
