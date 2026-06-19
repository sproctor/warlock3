package warlockfe.warlock3.compose.ui.settings

import warlockfe.warlock3.core.prefs.models.Action
import kotlin.uuid.Uuid

// Pure helpers shared by the mobile and desktop Actions settings editors. Actions form a flat pool
// referenced by id, so editing the toolbar or a group's children is editing a List<Uuid>.

/** Move the item at [from] to [to], returning a new list. No-op when an index is out of range. */
internal fun <T> List<T>.moveItem(
    from: Int,
    to: Int,
): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/** True if [target] is reachable from [start] by following group children in [pool] (cycle check). */
internal fun actionReaches(
    start: Uuid,
    target: Uuid,
    pool: Map<Uuid, Action>,
): Boolean {
    val visited = mutableSetOf<Uuid>()

    fun dfs(id: Uuid): Boolean {
        if (id == target) return true
        if (!visited.add(id)) return false
        return pool[id]?.children?.any { dfs(it) } ?: false
    }

    return dfs(start)
}

/**
 * The actions that may be added as a child of the group [groupId] without creating a cycle: every
 * pool action except the group itself and any action from which the group is already reachable.
 */
internal fun childCandidates(
    groupId: Uuid,
    pool: List<Action>,
): List<Action> {
    val byId = pool.associateBy { it.id }
    return pool.filter { it.id != groupId && !actionReaches(it.id, groupId, byId) }
}

/** Resolve a list of action ids against the [pool], dropping any that no longer exist. */
internal fun List<Uuid>.resolve(pool: Map<Uuid, Action>): List<Action> = mapNotNull { pool[it] }
