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
