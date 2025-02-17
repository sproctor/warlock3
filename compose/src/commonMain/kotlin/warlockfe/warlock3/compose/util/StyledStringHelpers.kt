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
): AnnotatedString {
    return buildAnnotatedString {
        substrings.forEach {
            append(it.toAnnotatedString(variables, styleMap, actionHandler))
        }
    }
}

fun StyleDefinition.toSpanStyle(): SpanStyle {
    return SpanStyle(
        color = textColor.toColor(),
        background = backgroundColor.toColor(),
        fontFamily = fontFamily?.let { createFontFamily(it) },
        textDecoration = if (underline) TextDecoration.Underline else null,
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        fontSize = fontSize?.sp ?: TextUnit.Unspecified
    )
}

fun WarlockStyle.toStyleDefinition(styleMap: Map<String, StyleDefinition>): StyleDefinition {
    return (styleMap[name] ?: StyleDefinition())
}

fun StyledStringLeaf.toAnnotatedString(
    variables: Map<String, StyledString>,
    styleMap: Map<String, StyleDefinition>,
    actionHandler: (WarlockAction) -> Unit,
): AnnotatedString {
    return buildAnnotatedString {
        val style = flattenStyles(styles.map { it.toStyleDefinition(styleMap) })
            ?.also { pushStyle(it.toSpanStyle()) }

        styles.forEach { style ->
            style.action?.let { action ->
                when (action) {
                    is WarlockAction.OpenLink ->
                        pushLink(LinkAnnotation.Url(action.url))

                    else -> pushLink(
                        LinkAnnotation.Clickable("action") {
                            actionHandler(action)
                        }
                    )
                }
            }
        }
        when (this@toAnnotatedString) {
            is StyledStringSubstring -> append(text)
            is StyledStringVariable ->
                // TODO: break circular references
                variables[name]?.toAnnotatedString(
                    variables = variables,
                    styleMap = styleMap,
                    actionHandler = actionHandler,
                )?.let {
                    append(it)
                }
        }
        if (style != null) {
            pop()
        }
        styles.forEach { style ->
            style.action?.let { _ -> pop() }
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
        is StyledStringVariable -> entireLineStyles + (variables[name]?.getEntireLineStyles(variables, styleMap)
            ?: emptyList())
    }
}
