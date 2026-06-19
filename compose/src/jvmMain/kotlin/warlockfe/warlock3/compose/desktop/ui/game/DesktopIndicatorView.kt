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
import warlockfe.warlock3.compose.generated.resources.death
import warlockfe.warlock3.compose.generated.resources.diseased
import warlockfe.warlock3.compose.generated.resources.hidden
import warlockfe.warlock3.compose.generated.resources.invisible
import warlockfe.warlock3.compose.generated.resources.joined
import warlockfe.warlock3.compose.generated.resources.kneeling
import warlockfe.warlock3.compose.generated.resources.local_hospital
import warlockfe.warlock3.compose.generated.resources.poisoned
import warlockfe.warlock3.compose.generated.resources.prone
import warlockfe.warlock3.compose.generated.resources.sitting
import warlockfe.warlock3.compose.generated.resources.standing
import warlockfe.warlock3.compose.generated.resources.stunned
import warlockfe.warlock3.compose.generated.resources.webbed

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
    // Posture and affliction share the first slot; the rest follow the design's grouping (6.4).
    val groups: List<List<Pair<String, DrawableResource>>> =
        listOf(
            listOf(
                "kneeling" to Res.drawable.kneeling,
                "prone" to Res.drawable.prone,
                "sitting" to Res.drawable.sitting,
                "standing" to Res.drawable.standing,
                "poisoned" to Res.drawable.poisoned,
                "diseased" to Res.drawable.diseased,
            ),
            listOf("joined" to Res.drawable.joined),
            listOf(
                "bleeding" to Res.drawable.local_hospital,
                "dead" to Res.drawable.death,
            ),
            listOf(
                "invisible" to Res.drawable.invisible,
                "hidden" to Res.drawable.hidden,
                "webbed" to Res.drawable.webbed,
            ),
            listOf("stunned" to Res.drawable.stunned),
        )

    val chrome = WarlockGameChrome
    val shape = RoundedCornerShape(5.dp)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        groups.forEach { group ->
            val active = group.firstOrNull { indicators.contains(it.first) }
            val accent = active?.let { indicatorAccent(it.first, chrome) } ?: inactiveAccent(chrome)
            Box(
                modifier =
                    Modifier
                        .size(indicatorSize)
                        .background(accent.background, shape)
                        .border(width = Dp.Hairline, color = accent.border, shape = shape)
                        .padding(indicatorSize / 6f),
                contentAlignment = Alignment.Center,
            ) {
                if (active != null) {
                    Image(
                        modifier = Modifier.fillMaxSize(),
                        painter = painterResource(active.second),
                        colorFilter = accent.iconTint?.let { ColorFilter.tint(it) },
                        contentDescription = active.first,
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

private fun inactiveAccent(chrome: WarlockGameChromeColors) =
    IndicatorAccent(
        border = chrome.border,
        background = chrome.panelAlt,
        iconTint = null,
    )

private fun indicatorAccent(
    key: String,
    chrome: WarlockGameChromeColors,
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
