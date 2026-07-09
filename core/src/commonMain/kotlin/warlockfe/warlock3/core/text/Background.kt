package warlockfe.warlock3.core.text

/**
 * A style layer's background, tri-state: an explicit [Fill] color, explicit transparent [None], or
 * [Unset] (contributes nothing — the layer below decides). "None because the user chose transparent"
 * must resolve differently from "None because nothing set a background", which the old
 * specified-or-unspecified [WarlockColor] could not express.
 */
sealed interface Background {
    data class Fill(
        val color: WarlockColor,
    ) : Background

    data object None : Background

    data object Unset : Background
}

/**
 * Interprets a stored [WarlockColor] as a tri-state background: unspecified -> [Background.Unset],
 * fully-transparent (argb 0) -> [Background.None], anything else -> [Background.Fill]. This lets the
 * existing single-color config field carry all three states with no schema/key change (old files only
 * ever hold "default" or an opaque color).
 */
fun WarlockColor.toBackground(): Background =
    when {
        isUnspecified() -> Background.Unset
        argb == 0L -> Background.None
        else -> Background.Fill(this)
    }

/** The [WarlockColor] encoding of a background: Fill -> its color, None -> transparent (argb 0), Unset -> unspecified. */
fun Background.toWarlockColor(): WarlockColor =
    when (this) {
        is Background.Fill -> color
        Background.None -> WarlockColor(0L)
        Background.Unset -> WarlockColor.Unspecified
    }
