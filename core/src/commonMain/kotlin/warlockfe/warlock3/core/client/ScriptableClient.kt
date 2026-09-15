package warlockfe.warlock3.core.client

import kotlinx.coroutines.flow.StateFlow
import warlockfe.warlock3.core.text.WarlockColor
import kotlin.time.Duration

/**
 * The parts of a client a script may drive. A Simutronics game states these in its protocol (the
 * roundtime, what spell is being cast) and a telnet MUD does not, so on a telnet connection a
 * script fills them in from the text or from GMCP - as a rule, the script the MUD itself sent
 * (see [mudScript]).
 */
interface ScriptableClient {
    /**
     * The script the MUD has offered to install, when it has: a `Client.GUI` GMCP message naming a
     * version and a URL, the way Mudlet is offered its interface packages. Null until one arrives,
     * and never set on a connection whose user declined scripts.
     */
    val mudScript: StateFlow<MudScriptOffer?>

    /** Sets when the roundtime ends, in seconds since the epoch, or clears it. */
    fun setRoundTime(endSeconds: Long?)

    /** Sets when the cast time ends, in seconds since the epoch, or clears it. */
    fun setCastTime(endSeconds: Long?)

    /** Sets what the left hand holds, or empties it. */
    fun setLeftHand(item: String?)

    /** Sets what the right hand holds, or empties it. */
    fun setRightHand(item: String?)

    /** Sets the spell readied, or clears it. */
    fun setSpellHand(spell: String?)

    /** Whether the hands row is shown at all; a MUD without hands can take it off the screen. */
    val handsShown: StateFlow<Boolean>

    fun showHands(shown: Boolean)

    /**
     * The blocks of the hands row in order: the hands and the spell, and any a script added with
     * [setBlock]. See [HandBlock].
     */
    val handBlocks: StateFlow<List<HandBlock>>

    /** Shows or hides one block, a hand or a script's own. */
    fun showBlock(
        id: String,
        shown: Boolean,
    )

    /**
     * Adds a block of the script's own to the end of the row, or updates it: [label] is written
     * before the [value], and kept as it was when null (the id, for a new block). Not for the
     * hands, which have their own setters.
     */
    fun setBlock(
        id: String,
        label: String?,
        value: String?,
    )

    /** Removes a block a script added; the hands stay, hidden or shown. */
    fun removeBlock(id: String)

    /** Puts the blocks named in [order] first, in that order; the rest follow as they were. */
    fun arrangeBlocks(order: List<String>)

    /**
     * Sets a bar in the vitals panel, adding it after the others if it is new: [percent] is how
     * full it is, [text] what is written on it (its [id] when null). GS4's ids - `health`, `mana`,
     * `stamina`, `spirit` - get GS4's colours and a skin's bar art.
     */
    suspend fun setVital(
        id: String,
        percent: Int,
        text: String?,
    )

    /** Removes every bar from the vitals panel. */
    suspend fun clearVitals()

    /**
     * Shows the background of the window named [window] as [color] for [total], fading to it over
     * the first [fadeIn] of that and back over the last [fadeOut]. The fades must fit in the total.
     */
    fun flashBackground(
        window: String,
        color: WarlockColor,
        total: Duration,
        fadeIn: Duration,
        fadeOut: Duration,
    )

    /**
     * Sends a GMCP message to the server: the package and message name (`Core.Supports.Add`) and
     * the JSON. Dropped when the server never negotiated GMCP.
     */
    suspend fun sendGmcp(
        name: String,
        data: String,
    )
}

/**
 * A script the MUD offered over GMCP: its version, and either where to download it ([url]) or the
 * Lua itself ([script]), one of which is always present. A new version replaces the last.
 */
data class MudScriptOffer(
    val version: String,
    val url: String? = null,
    val script: String? = null,
)
