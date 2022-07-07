package cc.warlock.warlock3.app.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import cc.warlock.warlock3.core.text.*

fun StyledString.toAnnotatedString(
    variables: Map<String, StyledString>,
    styleMap: Map<String, StyleDefinition>,
): AnnotatedString {
    return buildAnnotatedString {
        substrings.forEach {
            append(it.toAnnotatedString(variables, styleMap))
        }
    }
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
    return (styleMap[name] ?: StyleDefinition())
}

fun StyledStringLeaf.toAnnotatedString(
    variables: Map<String, StyledString>,
    styleMap: Map<String, StyleDefinition>,
): AnnotatedString {
    return buildAnnotatedString {
        styles.forEach { style ->
            val styleDef = style.toStyleDefinition(styleMap)
            pushStyle(styleDef.toSpanStyle())
            style.annotations?.forEach {
                pushStringAnnotation(it.first, it.second)
            }
        }
        when (this@toAnnotatedString) {
            is StyledStringSubstring -> append(text)
            is StyledStringVariable ->
                // TODO: break circular references
                variables[name]?.toAnnotatedString(
                    variables = variables,
                    styleMap = styleMap,
                )?.let {
                    append(it)
                }
        }
        styles.forEach { style ->
            pop()
            style.annotations?.forEach { _ -> pop() }
        }
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
