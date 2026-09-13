package warlockfe.warlock3.telnet.gmcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import warlockfe.warlock3.core.client.DataDistance
import warlockfe.warlock3.core.client.PanelObject
import warlockfe.warlock3.core.client.Percentage
import warlockfe.warlock3.core.compass.Direction
import kotlin.math.roundToInt

/** What a GMCP message changed, for the client to show. */
sealed interface GmcpUpdate {
    /** The exits of the room the character is in, as the compass names them. */
    data class Exits(
        val directions: Set<Direction>,
    ) : GmcpUpdate

    /**
     * The vitals bars, laid out side by side across the status bar the way GS4's minivitals panel
     * is: a skin entry naming the bar (`healthBar`, so a GS4 skin colours it) and the bar itself.
     */
    data class Vitals(
        val objects: List<PanelObject>,
    ) : GmcpUpdate

    /** What the character wields, by hand; null for an empty hand. */
    data class Hands(
        val left: String?,
        val right: String?,
    ) : GmcpUpdate
}

/**
 * Turns the GMCP messages a MUD sends into something the client can show.
 *
 * GMCP names the message (`Char.Vitals`) but not the shape of its data: every code base has its
 * own keys. So the vitals are matched by name against the spellings in use - `hp`, `mana`,
 * `moves`, `ep` - and the maxima are remembered across messages, because Aardwolf sends them
 * once in `Char.MaxStats` and never again. Package names are compared without regard to case,
 * since that server lowercases them.
 *
 * The hands come from the inventory (`Char.Items`, the IRE package): the server lists it once when
 * asked and then sends each change, and an item's `attrib` string carries `l` or `L` for the hand
 * that wields it, or `W` for a wielded item that names no hand.
 */
class GmcpHandler {
    private val json = Json { ignoreUnknownKeys = true }

    // Every numeric field seen in a vitals or maxima message so far, by lowercased key.
    private val vitals = HashMap<String, Double>()

    // The inventory as last told, by item id, in the order the server listed it.
    private val inventory = LinkedHashMap<String, Item>()

    private class Item(
        val id: String,
        val name: String,
        val attrib: String,
    )

    fun handle(
        name: String,
        data: String,
    ): GmcpUpdate? {
        val element = if (data.isBlank()) null else runCatching { json.parseToJsonElement(data) }.getOrNull()
        return when (name.lowercase()) {
            "room.info" -> (element as? JsonObject)?.let { exits(it) }
            "char.vitals", "char.maxstats" -> (element as? JsonObject)?.let { vitals(it) }
            "char.items.list" -> (element as? JsonObject)?.let { itemsList(it) }
            "char.items.add", "char.items.update" -> (element as? JsonObject)?.let { itemChanged(it) }
            "char.items.remove" -> (element as? JsonObject)?.let { itemRemoved(it) }
            else -> null
        }
    }

    private fun exits(room: JsonObject): GmcpUpdate.Exits {
        val names =
            when (val exits = room["exits"]) {
                // IRE, Aardwolf and most others: {"n": 1234, "s": 1235}, the values being room ids.
                is JsonObject -> exits.keys

                // A few servers list them instead.
                is JsonArray -> exits.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

                else -> emptyList()
            }
        return GmcpUpdate.Exits(names.mapNotNull { direction(it) }.toSet())
    }

    private fun itemsList(message: JsonObject): GmcpUpdate.Hands? {
        if (!message.isInventory()) return null
        inventory.clear()
        (message["items"] as? JsonArray)?.forEach { element ->
            (element as? JsonObject)?.toItem()?.let { inventory[it.id] = it }
        }
        return hands()
    }

    private fun itemChanged(message: JsonObject): GmcpUpdate.Hands? {
        if (!message.isInventory()) return null
        val item = (message["item"] as? JsonObject)?.toItem() ?: return null
        inventory[item.id] = item
        return hands()
    }

    private fun itemRemoved(message: JsonObject): GmcpUpdate.Hands? {
        if (!message.isInventory()) return null
        // The item goes as an object on the current servers and went as a bare id on older ones.
        val id =
            when (val item = message["item"]) {
                is JsonObject -> item.string("id")
                is JsonPrimitive -> item.contentOrNull
                else -> null
            } ?: return null
        inventory.remove(id)
        return hands()
    }

    private fun hands(): GmcpUpdate.Hands {
        val items = inventory.values
        var left = items.firstOrNull { 'l' in it.attrib }
        var right = items.firstOrNull { 'L' in it.attrib }
        // A wielded item with no hand named goes in whichever hand is free, the right one first.
        items.filter { 'W' in it.attrib && it !== left && it !== right }.forEach { item ->
            when {
                right == null -> right = item
                left == null -> left = item
            }
        }
        return GmcpUpdate.Hands(left = left?.name, right = right?.name)
    }

    private fun JsonObject.isInventory(): Boolean = string("location").equals("inv", ignoreCase = true)

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.toItem(): Item? {
        val id = string("id") ?: return null
        return Item(id = id, name = string("name") ?: id, attrib = string("attrib") ?: "")
    }

    private fun vitals(message: JsonObject): GmcpUpdate.Vitals? {
        var changed = false
        for ((key, value) in message) {
            val number = (value as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: continue
            vitals[key.lowercase()] = number
            changed = true
        }
        if (!changed) return null
        val bars =
            VITALS.mapNotNull { kind ->
                val current = kind.valueKeys.firstNotNullOfOrNull { vitals[it] } ?: return@mapNotNull null
                val max = kind.maxKeys.firstNotNullOfOrNull { vitals[it] }
                Bar(kind.id, current, max)
            }
        if (bars.isEmpty()) return null
        val width = 100 / bars.size
        return GmcpUpdate.Vitals(
            bars.flatMapIndexed { index, bar ->
                val left = DataDistance.Percent(Percentage(index * width))
                val size = DataDistance.Percent(Percentage(width))
                listOf(
                    PanelObject.Skin(
                        id = "${bar.id}Skin",
                        name = "${bar.id}Bar",
                        controls = listOf(bar.id),
                        left = left,
                        top = TOP,
                        width = size,
                        height = FULL,
                        align = null,
                        topAnchor = null,
                        leftAnchor = null,
                        tooltip = null,
                    ),
                    PanelObject.ProgressBar(
                        id = bar.id,
                        value = bar.percent,
                        text = bar.text,
                        left = left,
                        top = TOP,
                        width = size,
                        height = FULL,
                        align = null,
                        topAnchor = null,
                        leftAnchor = null,
                        tooltip = null,
                    ),
                )
            },
        )
    }

    private class Bar(
        val id: String,
        val current: Double,
        val max: Double?,
    ) {
        // Without a maximum there is no fraction to fill; a value that could be a percentage is
        // taken as one, anything else shows as a full bar with the number on it.
        val percent: Percentage
            get() =
                when {
                    max != null && max > 0 -> Percentage((current / max * 100).roundToInt().coerceIn(0, 100))
                    current in 0.0..100.0 -> Percentage(current.roundToInt())
                    else -> Percentage(100)
                }

        val text: String
            get() = if (max != null) "$id ${current.plain()}/${max.plain()}" else "$id ${current.plain()}"
    }

    private class VitalKind(
        val id: String,
        val valueKeys: List<String>,
        val maxKeys: List<String>,
    )

    private companion object {
        val TOP = DataDistance.Percent(Percentage(0))
        val FULL = DataDistance.Percent(Percentage(100))

        // The bars, in GS4's order, and the names the various code bases give each. The ids are
        // GS4's so a user's per-bar colours and a GS4 skin's bar entries apply to a MUD too.
        val VITALS =
            listOf(
                VitalKind(
                    id = "health",
                    valueKeys = listOf("hp", "health", "hits"),
                    maxKeys = listOf("maxhp", "max_hp", "hpmax", "maxhealth", "maxhits"),
                ),
                VitalKind(
                    id = "mana",
                    valueKeys = listOf("mp", "mana", "sp", "gp"),
                    maxKeys = listOf("maxmp", "max_mp", "mpmax", "maxmana", "max_mana", "maxsp", "maxgp"),
                ),
                VitalKind(
                    id = "stamina",
                    valueKeys = listOf("ep", "mv", "moves", "stamina", "endurance", "move", "vitality"),
                    maxKeys =
                        listOf("maxep", "max_ep", "maxmv", "maxmoves", "maxstamina", "maxendurance", "maxmove", "maxvitality"),
                ),
                VitalKind(
                    id = "spirit",
                    valueKeys = listOf("wp", "willpower", "spirit"),
                    maxKeys = listOf("maxwp", "max_wp", "maxwillpower", "maxspirit"),
                ),
            )

        val DIRECTIONS =
            mapOf(
                "n" to "n",
                "north" to "n",
                "ne" to "ne",
                "northeast" to "ne",
                "e" to "e",
                "east" to "e",
                "se" to "se",
                "southeast" to "se",
                "s" to "s",
                "south" to "s",
                "sw" to "sw",
                "southwest" to "sw",
                "w" to "w",
                "west" to "w",
                "nw" to "nw",
                "northwest" to "nw",
                "u" to "up",
                "up" to "up",
                "d" to "down",
                "down" to "down",
                "out" to "out",
            )

        fun direction(name: String): Direction? = DIRECTIONS[name.lowercase()]?.let { Direction(it) }

        fun Double.plain(): String = if (this == toLong().toDouble()) toLong().toString() else toString()
    }
}
