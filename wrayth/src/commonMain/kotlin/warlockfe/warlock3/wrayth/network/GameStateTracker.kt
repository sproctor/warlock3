package warlockfe.warlock3.wrayth.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import warlockfe.warlock3.core.client.Effect
import warlockfe.warlock3.core.client.EffectPanel
import warlockfe.warlock3.core.client.GameObject
import warlockfe.warlock3.core.client.GameState
import warlockfe.warlock3.core.client.Injury
import warlockfe.warlock3.core.client.PanelObject
import warlockfe.warlock3.core.client.RoomState
import warlockfe.warlock3.core.client.Vital
import warlockfe.warlock3.core.text.WarlockStyle
import warlockfe.warlock3.wrayth.protocol.WraythActionEvent
import warlockfe.warlock3.wrayth.protocol.WraythCompassEndEvent
import warlockfe.warlock3.wrayth.protocol.WraythComponentDefinitionEvent
import warlockfe.warlock3.wrayth.protocol.WraythComponentEndEvent
import warlockfe.warlock3.wrayth.protocol.WraythComponentStartEvent
import warlockfe.warlock3.wrayth.protocol.WraythCreatureStatusEvent
import warlockfe.warlock3.wrayth.protocol.WraythDataReceivedEvent
import warlockfe.warlock3.wrayth.protocol.WraythDialogDataEvent
import warlockfe.warlock3.wrayth.protocol.WraythDialogObjectEvent
import warlockfe.warlock3.wrayth.protocol.WraythDirectionEvent
import warlockfe.warlock3.wrayth.protocol.WraythEolEvent
import warlockfe.warlock3.wrayth.protocol.WraythEvent
import warlockfe.warlock3.wrayth.protocol.WraythLeftEvent
import warlockfe.warlock3.wrayth.protocol.WraythNavEvent
import warlockfe.warlock3.wrayth.protocol.WraythPopStyleEvent
import warlockfe.warlock3.wrayth.protocol.WraythPromptEvent
import warlockfe.warlock3.wrayth.protocol.WraythPushCmdEvent
import warlockfe.warlock3.wrayth.protocol.WraythPushStyleEvent
import warlockfe.warlock3.wrayth.protocol.WraythRightEvent
import warlockfe.warlock3.wrayth.protocol.WraythSpellEvent
import warlockfe.warlock3.wrayth.protocol.WraythStreamEvent
import warlockfe.warlock3.wrayth.protocol.WraythStreamWindowEvent
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Builds a [GameState] from the events [warlockfe.warlock3.wrayth.protocol.WraythProtocolHandler]
 * produces, alongside the client's own handling of them. It is fed every event of the connection in
 * order, on the read loop, and publishes what it has at the end of each line - so a reader never sees
 * a line half applied - and at once on a room change, so that anything reacting to the move reads
 * the new room number.
 *
 * The room contents come from the room components (`room objs`, `room players`, `room desc`, `room
 * exits`), which are text with links in them. To know which link a piece of text sits in, and
 * whether it is bold (a creature), this keeps its own copy of the style stack the client keeps, from
 * the same push and pop events.
 *
 * @param now the game's current time, which effect timers are counted from.
 */
class GameStateTracker(
    private val now: () -> Instant,
) {
    private val _state = MutableStateFlow(GameState.EMPTY)
    val state: StateFlow<GameState> = _state.asStateFlow()

    private var current = GameState.EMPTY

    private val marks = ArrayDeque<Mark>()

    private var componentId: String? = null
    private val segments = mutableListOf<Segment>()

    private var dialogId: String? = null
    private val compass = mutableSetOf<String>()

    fun handle(event: WraythEvent) {
        when (event) {
            is WraythPushStyleEvent -> {
                marks.addLast(if (event.style == WarlockStyle.Bold) Mark.Bold else Mark.Other)
            }

            is WraythPushCmdEvent -> {
                marks.addLast(Mark.Link(exist = event.cmd.exist, noun = event.cmd.noun))
            }

            WraythPopStyleEvent -> {
                marks.removeLastOrNull()
            }

            is WraythPromptEvent -> {
                // The client drops its styles and any open component here; so must we.
                marks.clear()
                discardComponent()
            }

            is WraythStreamEvent -> {
                discardComponent()
            }

            is WraythComponentStartEvent -> {
                openComponent(event.id)
            }

            is WraythComponentDefinitionEvent -> {
                openComponent(event.id)
            }

            WraythComponentEndEvent -> {
                closeComponent()
            }

            is WraythDataReceivedEvent -> {
                if (componentId != null) {
                    segments +=
                        Segment(
                            text = event.text,
                            link = marks.lastOrNull { it is Mark.Link } as Mark.Link?,
                            bold = marks.any { it == Mark.Bold },
                        )
                }
            }

            is WraythActionEvent -> {
                // A <d> tag: in the exits component, an exit.
                if (componentId != null) {
                    segments += Segment(text = event.text, link = null, bold = false, isAction = true)
                }
            }

            is WraythNavEvent -> {
                current =
                    current.copy(
                        room = RoomState(id = event.roomId),
                        creatureStatus = emptyMap(),
                    )
                publish()
            }

            is WraythStreamWindowEvent -> {
                val window = event.window
                if (window.name == "main" && window.subtitle != null) {
                    val (title, uid) = parseRoomTitle(window.subtitle)
                    current = current.copy(room = current.room.copy(title = title, uid = uid))
                }
            }

            is WraythDirectionEvent -> {
                compass += event.direction.value
            }

            WraythCompassEndEvent -> {
                current = current.copy(room = current.room.copy(compass = compass.toSet()))
                compass.clear()
            }

            is WraythLeftEvent -> {
                current = current.copy(leftHand = heldObject(event.value, event.exist, event.noun))
            }

            is WraythRightEvent -> {
                current = current.copy(rightHand = heldObject(event.value, event.exist, event.noun))
            }

            is WraythSpellEvent -> {
                current = current.copy(preparedSpell = event.value.takeUnless { it == "None" })
            }

            is WraythDialogDataEvent -> {
                val id = event.id
                dialogId = id
                if (id != null && event.clear) {
                    EffectPanel.fromDialogId(id)?.let { panel ->
                        current = current.copy(effects = current.effects - panel)
                    }
                }
            }

            is WraythDialogObjectEvent -> {
                handlePanelObject(event.data)
            }

            is WraythCreatureStatusEvent -> {
                val previous = current.creatureStatus[event.exist] ?: emptyMap()
                current =
                    current.copy(
                        creatureStatus = current.creatureStatus + (event.exist to previous + event.attributes),
                    )
            }

            is WraythEolEvent -> {
                publish()
            }

            else -> {}
        }
    }

    private fun publish() {
        _state.value = current
    }

    private fun handlePanelObject(data: PanelObject) {
        val dialog = dialogId ?: return
        when (data) {
            is PanelObject.ProgressBar -> {
                val panel = EffectPanel.fromDialogId(dialog)
                if (panel != null) {
                    val effect =
                        Effect(
                            id = data.id,
                            name = data.text ?: data.id,
                            percent = data.value.value,
                            expiresAt = data.time?.let { parseDuration(it) }?.let { now() + it },
                        )
                    val effects = current.effects[panel].orEmpty().filter { it.id != effect.id } + effect
                    current = current.copy(effects = current.effects + (panel to effects))
                } else {
                    val name = vitalNames[data.id] ?: return
                    current = current.copy(vitals = current.vitals + (name to parseVital(data)))
                }
            }

            is PanelObject.DropDownBox -> {
                if (data.id == "dDBTarget") {
                    current =
                        current.copy(
                            targetIds =
                                data.options
                                    .map { it.value }
                                    .filter { it.startsWith('#') }
                                    .map { it.drop(1) },
                            currentTarget = data.value?.takeUnless { it.equals("none", ignoreCase = true) },
                        )
                }
            }

            is PanelObject.Image -> {
                if (dialog == "injuries") {
                    val injury = data.name?.let { parseInjury(it) }
                    current =
                        current.copy(
                            injuries = if (injury != null) current.injuries + (data.id to injury) else current.injuries - data.id,
                        )
                }
            }

            else -> {}
        }
    }

    private fun openComponent(id: String) {
        componentId = id
        segments.clear()
    }

    private fun discardComponent() {
        componentId = null
        segments.clear()
    }

    private fun closeComponent() {
        val id = componentId ?: return
        val parts = segments.toList()
        discardComponent()
        val room = current.room
        current =
            current.copy(
                room =
                    when (id) {
                        "room objs" -> {
                            val objects = parseObjects(parts, statusPattern = creatureStatusPattern)
                            room.copy(
                                npcs = objects.filter { it.bold }.map { it.obj },
                                loot = objects.filterNot { it.bold }.map { it.obj },
                            )
                        }

                        "room players" -> {
                            // Characters have negative exist ids; anything else linked in the line
                            // (a mount, a disk) is not one.
                            room.copy(
                                pcs =
                                    parseObjects(parts, statusPattern = playerStatusPattern)
                                        .map { it.obj }
                                        .filter { it.id.startsWith('-') },
                            )
                        }

                        "room desc" -> {
                            room.copy(
                                description = parts.joinToString("") { it.text }.trim().ifEmpty { null },
                                descriptionObjects = parseObjects(parts, statusPattern = null).map { it.obj },
                            )
                        }

                        "room exits" -> {
                            room.copy(exits = parts.filter { it.isAction }.map { it.text.trim() })
                        }

                        else -> {
                            return
                        }
                    },
            )
    }

    private class Found(
        val obj: GameObject,
        val bold: Boolean,
    )

    /**
     * The linked objects in a room component, in order. The text after an object and before the next
     * link is what the game says about it ("that appears stunned", "who is sitting", "(dead)"), and
     * [statusPattern] picks its status out of that.
     */
    private fun parseObjects(
        parts: List<Segment>,
        statusPattern: Regex?,
    ): List<Found> {
        val found = mutableListOf<Found>()
        var name = StringBuilder()
        var link: Mark.Link? = null
        var bold = false
        val trailing = StringBuilder()

        fun finish() {
            val exist = link?.exist ?: return
            val status =
                statusPattern
                    ?.find(trailing)
                    ?.groupValues
                    ?.drop(1)
                    ?.firstOrNull { it.isNotEmpty() }
                    ?.trim()
            found += Found(GameObject(id = exist, noun = link?.noun, name = name.toString().trim(), status = status), bold)
        }

        for (part in parts) {
            val partLink = part.link
            if (partLink != null && partLink.exist != null) {
                if (partLink === link && trailing.isEmpty()) {
                    // More of the same link's text.
                    name.append(part.text)
                    bold = bold || part.bold
                } else {
                    finish()
                    link = partLink
                    name = StringBuilder(part.text)
                    bold = part.bold
                    trailing.clear()
                }
            } else if (link != null) {
                trailing.append(part.text)
            }
        }
        finish()
        return found
    }

    private sealed interface Mark {
        data object Bold : Mark

        data object Other : Mark

        // Compared by identity, deliberately: two links to the same object side by side are still
        // two pieces of text.
        class Link(
            val exist: String?,
            val noun: String?,
        ) : Mark
    }

    private class Segment(
        val text: String,
        val link: Mark.Link?,
        val bold: Boolean,
        val isAction: Boolean = false,
    )
}

// The protocol's bar ids, and the names scripts know them by.
private val vitalNames =
    mapOf(
        "health" to "health",
        "mana" to "mana",
        "stamina" to "stamina",
        "spirit" to "spirit",
        "concentration" to "concentration",
        "pbarStance" to "stance",
        "encumlevel" to "encumbrance",
        "mindState" to "mind",
    )

private val vitalValuesPattern = Regex("""(-?\d+)\s*/\s*(\d+)""")

private fun parseVital(bar: PanelObject.ProgressBar): Vital {
    val values = bar.text?.let { vitalValuesPattern.find(it) }
    return Vital(
        percent = bar.value.value,
        text = bar.text,
        current = values?.groupValues?.get(1)?.toIntOrNull(),
        max = values?.groupValues?.get(2)?.toIntOrNull(),
    )
}

// "that appears stunned", "that is flying around", "that appears to be frozen in place", "(dead)";
// the phrase runs to the next comma, "and", or full stop.
private val creatureStatusPattern =
    Regex("""^\s*(?:\(([^)]+)\)|that (?:is|appears)(?: to be)? (.+?)(?:,|\s+and\b|\.|$))""")

// "who is sitting", "who appears to be kneeling", "(kneeling)".
private val playerStatusPattern =
    Regex("""^\s*(?:\(([^)]+)\)|who (?:is|appears)(?: to be)? (.+?)(?:,|\s+and\b|\.|$))""")

// GemStone: "Town Square, Fountain - 1234"; DragonRealms: "[Town Green] (10007)".
private val gsRoomNumber = Regex("""^(.*?) - (\d+)$""")
private val drRoomTitle = Regex("""^\[(.*)\]\s*(?:\((\d+)\))?$""")

/**
 * The room's name and number from the main window's subtitle, which reads " - Town Square, Fountain"
 * in GemStone, with " - 1234" after it when the character has room numbers turned on, and
 * " - [Town Green] (10007)" in DragonRealms.
 */
internal fun parseRoomTitle(subtitle: String): Pair<String?, String?> {
    val text = subtitle.trim().removePrefix("-").trim()
    if (text.isEmpty()) return null to null
    val match = drRoomTitle.matchEntire(text) ?: gsRoomNumber.matchEntire(text) ?: return text to null
    return match.groupValues[1] to match.groupValues[2].ifEmpty { null }
}

private val durationPattern = Regex("""^(\d+):(\d{1,2}):(\d{1,2})$""")

private fun parseDuration(text: String) =
    durationPattern.matchEntire(text.trim())?.destructured?.let { (h, m, s) ->
        h.toInt().hours + m.toInt().minutes + s.toInt().seconds
    }

private val injuryPattern = Regex("""^(Injury|Scar)(\d)$""")

private fun parseInjury(name: String): Injury? {
    val (kind, severity) = injuryPattern.matchEntire(name)?.destructured ?: return null
    return Injury(kind = if (kind == "Scar") Injury.Kind.SCAR else Injury.Kind.INJURY, severity = severity.toInt())
}

// "Empty", with no exist id, is an empty hand.
private fun heldObject(
    text: String,
    exist: String?,
    noun: String?,
): GameObject? =
    if (exist == null) {
        text.takeUnless { it.isBlank() || it == "Empty" }?.let { GameObject(id = "", noun = noun, name = it) }
    } else {
        GameObject(id = exist, noun = noun, name = text)
    }
