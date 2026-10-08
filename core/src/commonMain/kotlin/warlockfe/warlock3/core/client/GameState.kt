package warlockfe.warlock3.core.client

import kotlin.time.Instant

/**
 * What the game has told the client about the character and its surroundings, in typed form, for
 * scripts. A Simutronics game states all of this in its protocol and [WarlockClient.gameState]
 * follows it; a client that has no such protocol (a telnet MUD) leaves it [EMPTY].
 *
 * It is a snapshot: each update replaces the whole value, so a reader never sees half of one.
 */
data class GameState(
    val room: RoomState = RoomState(),
    /** What each hand holds, or null when it is empty. The id is blank if the game gave none. */
    val leftHand: GameObject? = null,
    val rightHand: GameObject? = null,
    /** The spell prepared, or null when none is. */
    val preparedSpell: String? = null,
    /** The vitals bars by id: `health`, `mana`, `stamina`, `spirit`, `concentration`, `stance`, `encumbrance`, `mind`. */
    val vitals: Map<String, Vital> = emptyMap(),
    /** Active effects by the panel that lists them; see [EffectPanel]. */
    val effects: Map<EffectPanel, List<Effect>> = emptyMap(),
    /** The creatures the game offers as targets, as exist ids without the `#`. */
    val targetIds: List<String> = emptyList(),
    /** The name of the creature targeted, or null when nothing is. */
    val currentTarget: String? = null,
    /** Injuries and scars by body part (`head`, `rightArm`, `nsys`...); a healthy part is left out. */
    val injuries: Map<String, Injury> = emptyMap(),
    /**
     * What the game said about each creature by exist id, as the attributes of its `crtrStatus` tag
     * (`hostile`, `disengaged`, `dead`...). Not every game sends these; DragonRealms does.
     */
    val creatureStatus: Map<String, Map<String, String>> = emptyMap(),
) {
    companion object {
        val EMPTY = GameState()
    }
}

data class RoomState(
    /** The room number from `<nav rm>`, or null before the first one. */
    val id: String? = null,
    /** The room's name, from the main window's subtitle. */
    val title: String? = null,
    /**
     * The room number the subtitle shows, when it shows one: DragonRealms always does ("[Town
     * Green] (10007)"), GemStone when room numbers are turned on, and then it is the same as [id].
     */
    val uid: String? = null,
    val description: String? = null,
    /** The exits listed in the room text ("Obvious exits: north, out"). */
    val exits: List<String> = emptyList(),
    /** The compass directions lit, by their protocol names (`n`, `ne`, `up`, `out`...). */
    val compass: Set<String> = emptySet(),
    /** Creatures, shown in bold in the room text. */
    val npcs: List<GameObject> = emptyList(),
    /** Everything else in "You also see". */
    val loot: List<GameObject> = emptyList(),
    /** Other characters, from "Also here". */
    val pcs: List<GameObject> = emptyList(),
    /** The objects linked in the room description. */
    val descriptionObjects: List<GameObject> = emptyList(),
)

/**
 * Something in the game with an exist id: [id] is what a command names it by (`#1234`, without the
 * `#`), [noun] what it answers to, [name] how it reads. [status] is what the text said about it
 * after its name ("stunned", "dead", "sitting"), or null.
 */
data class GameObject(
    val id: String,
    val noun: String?,
    val name: String,
    val status: String? = null,
)

/**
 * One vitals bar: how full it is as a [percent], the [text] written on it, and the current and
 * maximum values when the text carries them ("mana 40/120").
 */
data class Vital(
    val percent: Int,
    val text: String?,
    val current: Int? = null,
    val max: Int? = null,
)

enum class EffectPanel(
    val dialogId: String,
) {
    SPELLS("Active Spells"),
    BUFFS("Buffs"),
    DEBUFFS("Debuffs"),
    COOLDOWNS("Cooldowns"),
    ;

    companion object {
        fun fromDialogId(id: String): EffectPanel? = entries.firstOrNull { it.dialogId == id }
    }
}

/**
 * An effect in one of the effect panels: its [id] (a spell number, or the game's own id for an
 * effect that is not a spell), its [name] as the panel shows it, how much of it is left as a
 * [percent], and when it runs out, in game time (see [WarlockClient.getCurrentTime]).
 */
data class Effect(
    val id: String,
    val name: String,
    val percent: Int,
    val expiresAt: Instant?,
)

/** A wound or scar on one body part, of [severity] 1 to 3. */
data class Injury(
    val kind: Kind,
    val severity: Int,
) {
    enum class Kind { INJURY, SCAR }
}
