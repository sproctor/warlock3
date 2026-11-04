package warlockfe.warlock3.core.client

import kotlin.time.Instant

/**
 * The last `<pulse>`: when it came, and whether the next pulse is mana's ([manaNext]) or
 * spirit/stamina's. The next one is due between [minSeconds] and [maxSeconds] after [time].
 */
data class PulseInfo(
    val time: Instant,
    val manaNext: Boolean,
    val minSeconds: Int,
    val maxSeconds: Int,
)
