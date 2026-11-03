package warlockfe.warlock3.wrayth.util

import warlockfe.warlock3.core.client.Objective
import warlockfe.warlock3.core.client.ObjectiveAction
import warlockfe.warlock3.core.client.ObjectiveReward
import warlockfe.warlock3.core.client.ObjectiveTask
import kotlin.time.Instant

/** One `<objective>` as gathered from an `<objectives>` batch, with its child tags in order. */
class ObjectiveBuilder(
    private val attributes: Map<String, String>,
) {
    private val tasks = mutableListOf<Map<String, String>>()
    private val rewards = mutableListOf<Map<String, String>>()
    private val actions = mutableListOf<Map<String, String>>()

    fun addChild(
        name: String,
        attributes: Map<String, String>,
    ) {
        when (name) {
            "task" -> tasks += attributes
            "reward" -> rewards += attributes
            "action" -> actions += attributes
        }
    }

    fun build(): Objective {
        val location = attributes["location"] ?: ""
        return Objective(
            id = attributes["id"] ?: "",
            type = attributes["type"]?.uppercase() ?: "",
            state = attributes["state"] ?: "",
            name = attributes["name"] ?: "",
            description = attributes["description"] ?: "",
            // The game says "variable" for a quest that has no one place
            location = if (location.trim().equals("variable", ignoreCase = true)) "" else location,
            cadence = attributes["cadence"]?.takeIf { it.isNotEmpty() },
            expires = attributes.epochSeconds("expires"),
            cooldownUntil = attributes.epochSeconds("cooldownuntil"),
            tasks =
                tasks.map {
                    ObjectiveTask(
                        text = it["text"] ?: "",
                        count = it["count"]?.toIntOrNull(),
                        max = it["max"]?.toIntOrNull(),
                        units = it["units"]?.takeIf { units -> units.isNotEmpty() },
                        done = it["done"].let { done -> done == "t" || done == "true" || done == "1" },
                    )
                },
            rewards =
                rewards.flatMap {
                    when (val type = it["type"]) {
                        "experience", "fame" -> {
                            listOfNotNull(
                                it["amount"]?.toIntOrNull()?.let { amount -> ObjectiveReward(type, amount, null) },
                            )
                        }

                        // One custom reward tag can list several, separated by commas
                        "custom" -> {
                            (it["label"] ?: "")
                                .split(',')
                                .map { label -> label.trim() }
                                .filter { label -> label.isNotEmpty() }
                                .map { label -> ObjectiveReward(type, null, label) }
                        }

                        else -> {
                            emptyList()
                        }
                    }
                },
            actions =
                actions.map {
                    ObjectiveAction(
                        type = it["type"] ?: "",
                        command = it["cmd"]?.takeIf { cmd -> cmd.isNotEmpty() },
                        tooltip = it["tooltip"]?.takeIf { tooltip -> tooltip.isNotEmpty() },
                    )
                },
        )
    }
}

private fun Map<String, String>.epochSeconds(name: String): Instant? =
    get(name)?.toLongOrNull()?.takeIf { it >= 0 }?.let { Instant.fromEpochSeconds(it) }

/**
 * The objectives list after one `<objectives>` batch. "delete-objective" removes the objectives it
 * names (by type and id, or by id alone when it gives no type), "patch-objective" replaces or adds
 * the ones it carries, and anything else - "full-refresh", or no action - replaces the whole list.
 */
fun applyObjectives(
    current: List<Objective>,
    action: String?,
    batch: List<Objective>,
): List<Objective> =
    when (action) {
        "delete-objective" -> {
            val keys = batch.map { it.key }.toSet()
            val bareIds = batch.filter { it.type.isEmpty() && it.id.isNotEmpty() }.map { it.id }.toSet()
            current.filter { it.key !in keys && it.id !in bareIds }
        }

        "patch-objective" -> {
            val updated = current.toMutableList()
            batch.forEach { objective ->
                val index = updated.indexOfFirst { it.key == objective.key }
                if (index >= 0) updated[index] = objective else updated += objective
            }
            updated
        }

        else -> {
            batch
        }
    }
