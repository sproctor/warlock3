package warlockfe.warlock3.stormfront.util

import warlockfe.warlock3.core.client.DataDistance
import warlockfe.warlock3.core.client.Percentage

fun parseDistance(text: String): DataDistance {
    return if (text.contains('%')) {
        DataDistance.Percent(Percentage.fromString(text))
    } else {
        DataDistance.Pixels(text.toIntOrNull() ?: 0)
    }
}
