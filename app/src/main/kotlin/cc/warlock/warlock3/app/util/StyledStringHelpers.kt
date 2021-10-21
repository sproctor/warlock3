package cc.warlock.warlock3.app.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import cc.warlock.warlock3.core.text.*


fun WarlockColor.toColor(): Color {
    return Color(red = red, green = green, blue = blue)
}

fun StyledString.toAnnotatedString(
    variables: Map<String, StyledString>,
    parentStyle: WarlockStyle? = null
): AnnotatedString {
    return substrings.map { it.toAnnotatedString(variables, parentStyle) }.reduceOrNull { acc, annotatedString ->
        acc + annotatedString
    } ?: AnnotatedString("")
}

fun WarlockStyle.toSpanStyle(): SpanStyle {
    return SpanStyle(
        color = textColor?.toColor() ?: Color.Unspecified,
        background = backgroundColor?.toColor() ?: Color.Unspecified,
        fontFamily = if (monospace) FontFamily.Monospace else null,
        textDecoration = if (underline) TextDecoration.Underline else null,
    )
}

fun StyledStringLeaf.toAnnotatedString(
    variables: Map<String, StyledString>,
    parentStyle: WarlockStyle?
): AnnotatedString {
    // FIXME: break circular references
    return when (this) {
        is StyledStringSubstring ->
            AnnotatedString(text = text, spanStyle = style?.toSpanStyle() ?: SpanStyle())
        is StyledStringVariable ->
            variables[name]?.toAnnotatedString(
                variables,
                parentStyle = flattenStyles(listOfNotNull(style, parentStyle))
            )
                ?: AnnotatedString("")
    }
}