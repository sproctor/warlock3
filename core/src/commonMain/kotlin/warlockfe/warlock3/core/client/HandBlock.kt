package warlockfe.warlock3.core.client

/**
 * One block of the hands row under the game text. The three the row always has - [LEFT], [RIGHT]
 * and [SPELL] - are drawn with their icons and show what [WarlockClient.leftHand] and the other
 * hand flows hold, so their [value] here is unused; a block a script added shows its [label]
 * before its [value]. A block that is not [shown] is left out of the row.
 */
data class HandBlock(
    val id: String,
    val label: String? = null,
    val value: String? = null,
    val shown: Boolean = true,
) {
    val isHand: Boolean get() = id in HAND_IDS

    companion object {
        const val LEFT = "left"
        const val RIGHT = "right"
        const val SPELL = "spell"
        val HAND_IDS = setOf(LEFT, RIGHT, SPELL)

        /** The row as every game starts with it: the two hands and the spell. */
        val HANDS = listOf(HandBlock(LEFT), HandBlock(RIGHT), HandBlock(SPELL))
    }
}
