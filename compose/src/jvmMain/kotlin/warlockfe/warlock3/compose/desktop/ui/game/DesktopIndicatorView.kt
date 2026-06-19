package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.compose.util.indicatorDrawable
import warlockfe.warlock3.compose.util.indicatorEntry
import warlockfe.warlock3.compose.util.indicatorIconModifier
import warlockfe.warlock3.compose.util.indicatorReference

/**
 * The five grouped status slots on the right of the control bar. Every active status in a slot is
 * drawn at its skin-defined position, so co-active statuses (e.g. a posture plus poisoned) are all
 * visible at once; a status with no skin offset fills the box (the single-status case). The slot's
 * highest-priority active status colors the box (amber for posture/concealment, red for health) and
 * each icon keeps its own accent tint; an inactive slot is an empty bordered box.
 */
@Composable
fun DesktopIndicatorView(
    indicatorSize: Dp,
    indicators: Set<String>,
    modifier: Modifier = Modifier,
) {
    // Grouping and priority stay in code; the per-status image and position come from the skin's
    // "indicator" section. Affliction is listed before posture so the box takes the danger color (and
    // poisoned/diseased are never dropped) even though a posture is almost always present too.
    val groups: List<List<String>> =
        listOf(
            listOf("poisoned", "diseased", "kneeling", "prone", "sitting", "standing"),
            listOf("joined"),
            listOf("bleeding", "dead"),
            listOf("invisible", "hidden", "webbed"),
            listOf("stunned"),
        )

    val chrome = gameChrome
    val skin = LocalSkin.current
    val reference = skin.indicatorReference()
    val shape = RoundedCornerShape(5.dp)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        groups.forEach { group ->
            val active = group.filter { indicators.contains(it) }
            val accent = active.firstOrNull()?.let { indicatorAccent(it, chrome) } ?: inactiveAccent(chrome)
            Box(
                modifier =
                    Modifier
                        .size(indicatorSize)
                        .clip(shape)
                        .background(accent.background, shape)
                        .border(width = Dp.Hairline, color = accent.border, shape = shape),
            ) {
                active.forEach { status ->
                    val entry = skin.indicatorEntry(status) ?: return@forEach
                    val drawable = entry.indicatorDrawable() ?: return@forEach
                    Image(
                        modifier = indicatorIconModifier(entry, indicatorSize, reference),
                        painter = painterResource(drawable),
                        colorFilter = indicatorAccent(status, chrome).iconTint?.let { ColorFilter.tint(it) },
                        contentDescription = status,
                    )
                }
            }
        }
    }
}

private data class IndicatorAccent(
    val border: Color,
    val background: Color,
    /** null leaves the icon's own colors intact (full-color status assets). */
    val iconTint: Color?,
)

private fun inactiveAccent(chrome: GameChrome) =
    IndicatorAccent(
        border = chrome.border,
        background = chrome.panelAlt,
        iconTint = null,
    )

private fun indicatorAccent(
    key: String,
    chrome: GameChrome,
): IndicatorAccent =
    when (key) {
        "poisoned", "diseased" -> {
            IndicatorAccent(
                border = chrome.dangerBorder,
                background = chrome.dangerBackground,
                iconTint = null,
            )
        }

        "bleeding" -> {
            IndicatorAccent(
                border = chrome.dangerBorder,
                background = chrome.dangerBackground,
                iconTint = chrome.dangerIcon,
            )
        }

        "dead" -> {
            IndicatorAccent(
                border = chrome.dangerBorder,
                background = chrome.dangerBackground,
                iconTint = chrome.textPrimary,
            )
        }

        "joined" -> {
            IndicatorAccent(
                border = chrome.accent,
                background = chrome.accent.copy(alpha = 0.22f),
                iconTint = chrome.accentSubtle,
            )
        }

        // hidden has a full-color asset; leave it untinted on the amber slot.
        "hidden" -> {
            IndicatorAccent(
                border = chrome.litBorder,
                background = chrome.litBackground,
                iconTint = null,
            )
        }

        // postures, invisible, webbed, stunned
        else -> {
            IndicatorAccent(
                border = chrome.litBorder,
                background = chrome.litBackground,
                iconTint = chrome.litIcon,
            )
        }
    }
