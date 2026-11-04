package warlockfe.warlock3.core.client

/**
 * What a `<crtrStatus>` tag says about one creature in the room, keyed by its [exist] id. [effects]
 * holds every other flag the tag sets to 1 (stunned, prone, webbed...) and its `condition`, if any.
 * Health is only sent for some creatures.
 */
data class CreatureStatus(
    val exist: String,
    val hostile: Boolean,
    val disengaged: Boolean,
    val effects: List<String>,
    val health: Int?,
    val maxHealth: Int?,
)
