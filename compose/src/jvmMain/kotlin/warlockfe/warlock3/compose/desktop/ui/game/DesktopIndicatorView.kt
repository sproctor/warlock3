package warlockfe.warlock3.compose.desktop.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.allDrawableResources
import warlockfe.warlock3.compose.model.SkinObject
import warlockfe.warlock3.compose.util.LocalSkin
import warlockfe.warlock3.core.util.getIgnoringCase

/**
 * The five grouped status slots on the right of the control bar. Each group shows at most one active
 * status; an active slot is tinted with an accent (amber for posture/concealment, red for health),
 * an inactive slot is an empty bordered box.
 */
@Composable
fun DesktopIndicatorView(
    indicatorSize: Dp,
    indicators: Set<String>,
    modifier: Modifier = Modifier,
) {
    // Grouping and priority stay in code; the image used for each status is looked up from the skin's
    // "indicator" section (status name -> SkinImage referencing a drawable). Posture and affliction
    // share the first slot and only the first matching status in a group is shown, so affliction is
    // listed before posture: a posture (usually "standing") is almost always present and would
    // otherwise permanently mask the more important poisoned/diseased warning.
    val groups: List<List<String>> =
        listOf(
            listOf("poisoned", "diseased", "kneeling", "prone", "sitting", "standing"),
            listOf("joined"),
            listOf("bleeding", "dead"),
            listOf("invisible", "hidden", "webbed"),
            listOf("stunned"),
        )

    val chrome = gameChrome
    val indicatorImages = LocalSkin.current.getIgnoringCase("indicator")?.children
    val shape = RoundedCornerShape(5.dp)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        groups.forEach { group ->
            val activeKey = group.firstOrNull { indicators.contains(it) }
            val accent = activeKey?.let { indicatorAccent(it, chrome) } ?: inactiveAccent(chrome)
            Box(
                modifier =
                    Modifier
                        .size(indicatorSize)
                        .background(accent.background, shape)
                        .border(width = Dp.Hairline, color = accent.border, shape = shape)
                        .padding(indicatorSize / 6f),
                contentAlignment = Alignment.Center,
            ) {
                val drawable = activeKey?.let { indicatorImages?.indicatorDrawable(it) }
                if (activeKey != null && drawable != null) {
                    Image(
                        modifier = Modifier.fillMaxSize(),
                        painter = painterResource(drawable),
                        colorFilter = accent.iconTint?.let { ColorFilter.tint(it) },
                        contentDescription = activeKey,
                    )
                }
            }
        }
    }
}

/** Resolves a status key to its drawable via the skin's "indicator" section (the image `name`). */
private fun Map<String, SkinObject>.indicatorDrawable(status: String): DrawableResource? =
    getIgnoringCase(status)?.image?.name?.let { Res.allDrawableResources[it] }

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
