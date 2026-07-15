package warlockfe.warlock3.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import warlockfe.warlock3.compose.util.toColor
import warlockfe.warlock3.core.text.Background
import warlockfe.warlock3.core.text.ResolvedStyle
import warlockfe.warlock3.core.text.toHexString

/**
 * An honest miniature of how a style renders **in game**: a fixed-width swatch that composites the
 * resolved style over the effective game window background ([windowBackground]) - never the settings
 * panel surface, which would misreport legibility. The style is applied to [sample]'s styled fragment
 * only, so a preset that marks part of a line (`bold`, `speech`) previews the way it actually arrives.
 * A single hairline separates the chip from the panel; whether the background is explicitly set or
 * inherited is reported by the row's text label, not by the chip's own border.
 *
 * The chip is deliberately allowed to be unreadable - that is real information. The item's *name* is
 * rendered elsewhere in normal UI color and stays legible; this shows only the style. Callers pass the
 * already-resolved style and window background; the chip never walks the cascade itself.
 */
@Composable
fun StyleChip(
    resolved: ResolvedStyle,
    windowBackground: Color,
    // Fixed swatch size by default so list rows align into a swatch column; the editor's larger preview
    // overrides this with a full-width, taller sample.
    modifier: Modifier = Modifier.width(72.dp).height(28.dp),
    sample: StyleSample = StyleSample("Aa"),
    // The style the sample's unstyled text renders in - the window's default text. Null falls back to a
    // legible color for the field, which is right for samples that have no unstyled text.
    baseStyle: ResolvedStyle? = null,
) {
    // A whole-line style fills the chip; one that covers only a fragment leaves the line on the window
    // background and paints its fill behind that fragment alone, as it would in game.
    // Fill = the set color; None/unset both show the window background (None is transparent over it).
    val field =
        if (sample.isWholeLine) {
            when (val bg = resolved.background) {
                is Background.Fill -> bg.color.toColor(default = windowBackground)
                Background.None -> windowBackground
                Background.Unset -> windowBackground
            }
        } else {
            windowBackground
        }
    val legible = if (field.luminance() > 0.5f) Color.Black else Color.White
    val fragmentBackground =
        if (sample.isWholeLine) Color.Unspecified else (resolved.background as? Background.Fill)?.color.toColor()

    val text =
        buildAnnotatedString {
            val plain = SpanStyle(color = baseStyle?.textColor.toColor(default = legible))
            if (sample.prefix.isNotEmpty()) withStyle(plain) { append(sample.prefix) }
            withStyle(
                SpanStyle(
                    color = resolved.textColor.toColor(default = legible),
                    background = fragmentBackground,
                    fontWeight = resolved.weight?.let { FontWeight(it) },
                    fontStyle = if (resolved.italic) FontStyle.Italic else null,
                    textDecoration = if (resolved.underline) TextDecoration.Underline else null,
                ),
            ) { append(sample.styled) }
            if (sample.suffix.isNotEmpty()) withStyle(plain) { append(sample.suffix) }
        }

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                // Hairline: chip <-> panel. Panel-agnostic neutral so it reads on light and dark themes.
                .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .background(field),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = text, style = TextStyle(fontSize = 13.sp))
    }
}

/**
 * A preview line: [styled] is the fragment the style actually applies to, with [prefix]/[suffix] the
 * ordinary text around it. Mirrors how the line arrives in game - `bold` marks only the creature name,
 * `speech` only the "You say" verb - so the preview shows the style in context rather than repainting a
 * whole line that would never be styled end to end.
 */
data class StyleSample(
    val styled: String,
    val prefix: String = "",
    val suffix: String = "",
) {
    /** True when the style covers the entire line, and so may fill the chip's background. */
    val isWholeLine: Boolean
        get() = prefix.isEmpty() && suffix.isEmpty()
}

/**
 * Sample for previews that aren't tied to a single preset - window styles, names, highlights. A pangram
 * rather than a game line: those styles apply to arbitrary text, and it exercises every letter, which is
 * what makes a font/weight/italic change legible in a one-line preview.
 */
val GENERIC_SAMPLE = StyleSample("The quick brown fox jumps over the lazy dog.")

/** Sample for the base "default text" style: ordinary unstyled game output. */
val BASE_SAMPLE = StyleSample("This is the room description for some room in Riverhaven.")

/**
 * The line each preset actually styles in game, so a preview reads like real output instead of
 * placeholder text. Carried over from the pre-rewrite Appearance preview, which rendered one such line
 * per preset; `echo`/`error`/`link` had no line there and are taken from where the client emits them.
 */
fun sampleFor(presetName: String): StyleSample =
    when (presetName) {
        "roomName" -> StyleSample("[Riverhaven, Crescent Way]")
        "bold" -> StyleSample("Sir Robyn", prefix = "You also see a ", suffix = ".")
        "speech" -> StyleSample("You say", suffix = ", \"Hello.\"")
        "whisper" -> StyleSample("Someone whispers", suffix = ", \"Hi\"")
        "thought" -> StyleSample("Your mind hears Someone thinking, \"hello everyone\"")
        "watching" -> StyleSample("Some text you are watching")
        "command" -> StyleSample("say Hello")
        "echo" -> StyleSample("Starting script: hunting")
        "error" -> StyleSample("Script error: unknown command")
        "link" -> StyleSample("https://warlockfe.github.io/")
        else -> GENERIC_SAMPLE
    }

/** The trailing background label for a list row: `no bg` (inherit), `none` (transparent), or the hex fill. */
fun backgroundLabel(background: Background): String =
    when (background) {
        is Background.Fill -> background.color.toHexString() ?: "#??????"
        Background.None -> "none"
        Background.Unset -> "no bg"
    }

/** The "Inherit" option's annotation in the background picker: what the background falls back to. */
fun inheritedBackgroundLabel(inherited: Background): String =
    when (inherited) {
        is Background.Fill -> "inherits ${inherited.color.toHexString() ?: "a color"}"
        Background.None -> "inherits none (transparent)"
        Background.Unset -> "inherits the window background"
    }
