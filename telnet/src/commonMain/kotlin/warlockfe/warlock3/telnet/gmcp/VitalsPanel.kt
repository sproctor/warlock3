package warlockfe.warlock3.telnet.gmcp

import warlockfe.warlock3.core.client.DataDistance
import warlockfe.warlock3.core.client.PanelObject
import warlockfe.warlock3.core.client.Percentage

/** One bar of the vitals panel: how full, and what is written on it. */
data class VitalBar(
    val id: String,
    val percent: Percentage,
    val text: String?,
)

/**
 * The vitals [bars] laid out side by side across the status bar the way GS4's minivitals panel
 * is: a skin entry naming each bar (`healthBar`, so a GS4 skin colours it) and the bar itself.
 */
fun vitalsPanelObjects(bars: Collection<VitalBar>): List<PanelObject> {
    if (bars.isEmpty()) return emptyList()
    val width = 100 / bars.size
    return bars.flatMapIndexed { index, bar ->
        val left = DataDistance.Percent(Percentage(index * width))
        val size = DataDistance.Percent(Percentage(width))
        listOf(
            PanelObject.Skin(
                id = "${bar.id}Skin",
                name = "${bar.id}Bar",
                controls = listOf(bar.id),
                left = left,
                top = TOP,
                width = size,
                height = FULL,
                align = null,
                topAnchor = null,
                leftAnchor = null,
                tooltip = null,
            ),
            PanelObject.ProgressBar(
                id = bar.id,
                value = bar.percent,
                text = bar.text,
                left = left,
                top = TOP,
                width = size,
                height = FULL,
                align = null,
                topAnchor = null,
                leftAnchor = null,
                tooltip = null,
            ),
        )
    }
}

private val TOP = DataDistance.Percent(Percentage(0))
private val FULL = DataDistance.Percent(Percentage(100))
