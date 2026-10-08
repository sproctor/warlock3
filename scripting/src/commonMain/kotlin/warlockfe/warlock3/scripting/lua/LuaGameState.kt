package warlockfe.warlock3.scripting.lua

import warlockfe.warlock3.core.client.EffectPanel
import warlockfe.warlock3.core.client.GameObject
import warlockfe.warlock3.core.client.GameState
import warlockfe.warlock3.core.client.Injury
import warlockfe.warlock3.core.client.TextLink
import warlockfe.warlock3.core.client.WarlockClient
import kotlin.time.Instant

/**
 * The parts of a [GameState] scripts ask for, as plain maps and lists for [luaLiteral]. Field names
 * are the ones `docs/lua-api.md` documents.
 */
internal object LuaGameState {
    fun room(state: GameState): Map<String, Any?> {
        val room = state.room
        return mapOf(
            "id" to room.id,
            "uid" to room.uid,
            "title" to room.title,
            "description" to room.description,
            "exits" to room.exits,
            "compass" to room.compass.toList(),
            "objects" to room.descriptionObjects.map { obj(it) },
        )
    }

    /** The creatures, each with the game's `crtrStatus` attributes for it, when it sent any, as `flags`. */
    fun npcs(state: GameState): List<Map<String, Any?>> =
        state.room.npcs.map { npc -> obj(npc) + ("flags" to state.creatureStatus[npc.id]) }

    fun loot(state: GameState): List<Map<String, Any?>> = state.room.loot.map { obj(it) }

    fun pcs(state: GameState): List<Map<String, Any?>> = state.room.pcs.map { obj(it) }

    fun hands(state: GameState): Map<String, Any?> =
        mapOf(
            "left" to state.leftHand?.let { obj(it) },
            "right" to state.rightHand?.let { obj(it) },
            "spell" to state.preparedSpell,
        )

    fun vitals(state: GameState): Map<String, Any?> =
        state.vitals.mapValues { (_, vital) ->
            mapOf(
                "percent" to vital.percent,
                "text" to vital.text,
                "current" to vital.current,
                "max" to vital.max,
            )
        }

    /** The status icons lit, each `true`: `stunned`, `kneeling`, `hidden`, `webbed`... */
    fun status(client: WarlockClient): Map<String, Any?> = client.indicators.value.associateWith { true }

    /** One panel's effects, or every panel's by name when [panelName] is null. Null for an unknown panel. */
    fun effects(
        state: GameState,
        panelName: String?,
        now: Instant,
    ): Any? {
        fun effectsOf(panel: EffectPanel) =
            state.effects[panel].orEmpty().map { effect ->
                mapOf(
                    "id" to effect.id,
                    "name" to effect.name,
                    "percent" to effect.percent,
                    "remaining" to effect.expiresAt?.let { ((it - now).inWholeMilliseconds / 1000.0).coerceAtLeast(0.0) },
                )
            }
        if (panelName == null) {
            return EffectPanel.entries.associate { luaPanelName(it) to effectsOf(it) }
        }
        val panel = EffectPanel.entries.firstOrNull { luaPanelName(it) == panelName.lowercase() } ?: return null
        return effectsOf(panel)
    }

    fun targets(state: GameState): Map<String, Any?> =
        mapOf(
            "ids" to state.targetIds,
            "current" to state.currentTarget,
        )

    fun injuries(state: GameState): Map<String, Any?> =
        state.injuries.mapValues { (_, injury) ->
            mapOf(
                "kind" to if (injury.kind == Injury.Kind.SCAR) "scar" else "injury",
                "severity" to injury.severity,
            )
        }

    fun links(links: List<TextLink>): List<Map<String, Any?>> = links.map { mapOf("id" to it.id, "noun" to it.noun, "name" to it.text) }

    private fun obj(obj: GameObject): Map<String, Any?> =
        mapOf(
            "id" to obj.id.ifEmpty { null },
            "noun" to obj.noun,
            "name" to obj.name,
            "status" to obj.status,
        )

    private fun luaPanelName(panel: EffectPanel): String =
        when (panel) {
            EffectPanel.SPELLS -> "spells"
            EffectPanel.BUFFS -> "buffs"
            EffectPanel.DEBUFFS -> "debuffs"
            EffectPanel.COOLDOWNS -> "cooldowns"
        }
}
