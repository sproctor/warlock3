package cc.warlock.warlock3.app.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import cc.warlock.warlock3.core.text.*

fun StyledString.toAnnotatedString(
    variables: Map<String, StyledString>,
    styleMap: Map<String, StyleDefinition>,
    parentStyles: List<StyleDefinition> = emptyList()
): AnnotatedString {
    return substrings.map { it.toAnnotatedString(variables, parentStyles, styleMap) }.reduceOrNull { acc, annotatedString ->
        acc + annotatedString
    } ?: AnnotatedString("")
}

fun StyleDefinition.toSpanStyle(): SpanStyle {
    return SpanStyle(
        color = textColor.toColor(),
        background = backgroundColor.toColor(),
        fontFamily = if (monospace) FontFamily.Monospace else null,
        textDecoration = if (underline) TextDecoration.Underline else null,
    )
}

fun WarlockStyle.toStyleDefinition(styleMap: Map<String, StyleDefinition>): StyleDefinition {
    return styleMap[name] ?: StyleDefinition()
}

fun StyledStringLeaf.toAnnotatedString(
    variables: Map<String, StyledString>,
    parentStyles: List<StyleDefinition>,
    styleMap: Map<String, StyleDefinition>,
): AnnotatedString {
    // FIXME: break circular references
    val styleDefs = styles.map { it.toStyleDefinition(styleMap) } + parentStyles
    return when (this) {
        is StyledStringSubstring ->
            AnnotatedString(text = text, spanStyle = flattenStyles(styleDefs)?.toSpanStyle() ?: SpanStyle())
        is StyledStringVariable ->
            variables[name]?.toAnnotatedString(
                variables = variables,
                parentStyles = styleDefs,
                styleMap = styleMap,
            )
                ?: AnnotatedString("")
    }
}

fun StyledString.getEntireLineStyles(
    variables: Map<String, StyledString>,
    styleMap: Map<String, StyleDefinition>,
): List<StyleDefinition> {
    return substrings.flatMap { substring -> substring.getEntireLineStyles(variables, styleMap) }
}

fun StyledStringLeaf.getEntireLineStyles(
    variables: Map<String, StyledString>,
    styleMap: Map<String, StyleDefinition>,
): List<StyleDefinition> {
    val entireLineStyles = styles.mapNotNull { styleMap[it.name] }.filter { it.entireLine }
    return when (this) {
        is StyledStringSubstring -> entireLineStyles
        is StyledStringVariable -> entireLineStyles + (variables[name]?.getEntireLineStyles(variables, styleMap) ?: emptyList())
    }
}
