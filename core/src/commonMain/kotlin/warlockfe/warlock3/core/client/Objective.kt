package warlockfe.warlock3.core.client

import kotlin.time.Instant

/**
 * A quest, bounty or society task from `<objectives>` (GemStone). [type] is upper case (QUEST,
 * BOUNTY, SOCIETY...), and [key] - type and id together - is what the game's patches and deletes
 * name it by.
 */
data class Objective(
    val id: String,
    val type: String,
    val state: String,
    val name: String,
    val description: String,
    val location: String,
    val cadence: String?,
    val expires: Instant?,
    val cooldownUntil: Instant?,
    val tasks: List<ObjectiveTask>,
    val rewards: List<ObjectiveReward>,
    val actions: List<ObjectiveAction>,
) {
    val key: String get() = "$type-$id"
}

data class ObjectiveTask(
    val text: String,
    val count: Int?,
    val max: Int?,
    val units: String?,
    val done: Boolean,
)

/** An experience or fame reward has an [amount]; a custom one has only a [label]. */
data class ObjectiveReward(
    val type: String,
    val amount: Int?,
    val label: String?,
)

/** Something the player can do with the objective (accept, abandon...), sent as [command]. */
data class ObjectiveAction(
    val type: String,
    val command: String?,
    val tooltip: String?,
)
