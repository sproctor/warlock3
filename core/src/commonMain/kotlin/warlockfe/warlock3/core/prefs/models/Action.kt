package warlockfe.warlock3.core.prefs.models

import kotlin.uuid.Uuid

/**
 * A user-defined action button. A leaf action ([script] non-null) runs a WSL script when pressed; a
 * group action ([script] null) opens a drill-down menu of the actions referenced by [children].
 *
 * Actions live in a flat per-character pool and reference each other by id, so the same action can sit
 * on the toolbar and inside one or more groups. References that don't resolve are skipped at render
 * time, so a deleted action never breaks a group that still points at it.
 */
data class Action(
    val id: Uuid,
    val name: String,
    val script: String?,
    val children: List<Uuid> = emptyList(),
) {
    val isGroup: Boolean get() = script == null
}

/**
 * The resolved actions for the game-screen button bar: the full [actions] pool keyed by id (for
 * resolving a group's children) and the ordered [toolbar] of top-level actions to draw as buttons.
 */
data class ActionBar(
    val actions: Map<Uuid, Action>,
    val toolbar: List<Action>,
) {
    companion object {
        val EMPTY = ActionBar(actions = emptyMap(), toolbar = emptyList())
    }
}
