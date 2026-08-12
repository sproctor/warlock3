package warlockfe.warlock3.wrayth.protocol

import co.touchlab.kermit.Logger
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import warlockfe.warlock3.wrayth.parsers.generated.WraythLexer
import warlockfe.warlock3.wrayth.parsers.generated.WraythParser
import warlockfe.warlock3.wrayth.protocol.elements.AHandler
import warlockfe.warlock3.wrayth.protocol.elements.AppHandler
import warlockfe.warlock3.wrayth.protocol.elements.BHandler
import warlockfe.warlock3.wrayth.protocol.elements.BackgroundHandler
import warlockfe.warlock3.wrayth.protocol.elements.CastTimeHandler
import warlockfe.warlock3.wrayth.protocol.elements.ClearContainerHandler
import warlockfe.warlock3.wrayth.protocol.elements.ClearStreamHandler
import warlockfe.warlock3.wrayth.protocol.elements.CliHandler
import warlockfe.warlock3.wrayth.protocol.elements.CloseDialogHandler
import warlockfe.warlock3.wrayth.protocol.elements.CmdButtonHandler
import warlockfe.warlock3.wrayth.protocol.elements.CmdlistHandler
import warlockfe.warlock3.wrayth.protocol.elements.CompDefHandler
import warlockfe.warlock3.wrayth.protocol.elements.CompassHandler
import warlockfe.warlock3.wrayth.protocol.elements.ComponentHandler
import warlockfe.warlock3.wrayth.protocol.elements.ContainerHandler
import warlockfe.warlock3.wrayth.protocol.elements.DHandler
import warlockfe.warlock3.wrayth.protocol.elements.DialogDataHandler
import warlockfe.warlock3.wrayth.protocol.elements.DirHandler
import warlockfe.warlock3.wrayth.protocol.elements.DropDownBoxHandler
import warlockfe.warlock3.wrayth.protocol.elements.DynaStreamHandler
import warlockfe.warlock3.wrayth.protocol.elements.ImageHandler
import warlockfe.warlock3.wrayth.protocol.elements.IndicatorHandler
import warlockfe.warlock3.wrayth.protocol.elements.InvHandler
import warlockfe.warlock3.wrayth.protocol.elements.LabelHandler
import warlockfe.warlock3.wrayth.protocol.elements.LaunchURLHandler
import warlockfe.warlock3.wrayth.protocol.elements.LeftHandler
import warlockfe.warlock3.wrayth.protocol.elements.LinkHandler
import warlockfe.warlock3.wrayth.protocol.elements.MenuHandler
import warlockfe.warlock3.wrayth.protocol.elements.MenuImageHandler
import warlockfe.warlock3.wrayth.protocol.elements.MenuLinkHandler
import warlockfe.warlock3.wrayth.protocol.elements.MiHandler
import warlockfe.warlock3.wrayth.protocol.elements.ModeHandler
import warlockfe.warlock3.wrayth.protocol.elements.NavHandler
import warlockfe.warlock3.wrayth.protocol.elements.OpenDialogHandler
import warlockfe.warlock3.wrayth.protocol.elements.OutputHandler
import warlockfe.warlock3.wrayth.protocol.elements.PopBoldHandler
import warlockfe.warlock3.wrayth.protocol.elements.PopStreamHandler
import warlockfe.warlock3.wrayth.protocol.elements.PresetHandler
import warlockfe.warlock3.wrayth.protocol.elements.ProgressBarHandler
import warlockfe.warlock3.wrayth.protocol.elements.PromptHandler
import warlockfe.warlock3.wrayth.protocol.elements.PushBoldHandler
import warlockfe.warlock3.wrayth.protocol.elements.PushStreamHandler
import warlockfe.warlock3.wrayth.protocol.elements.RadioHandler
import warlockfe.warlock3.wrayth.protocol.elements.ResourceHandler
import warlockfe.warlock3.wrayth.protocol.elements.RightHandler
import warlockfe.warlock3.wrayth.protocol.elements.RoundTimeHandler
import warlockfe.warlock3.wrayth.protocol.elements.SettingsInfoHandler
import warlockfe.warlock3.wrayth.protocol.elements.SkinHandler
import warlockfe.warlock3.wrayth.protocol.elements.SpellHandler
import warlockfe.warlock3.wrayth.protocol.elements.StreamHandler
import warlockfe.warlock3.wrayth.protocol.elements.StreamWindowHandler
import warlockfe.warlock3.wrayth.protocol.elements.StyleHandler
import warlockfe.warlock3.wrayth.protocol.elements.UpDownEditBoxHandler
import warlockfe.warlock3.wrayth.protocol.elements.UpdateVerbsHandler

class WraythProtocolHandler {
    private val logger = Logger.withTag("WraythProtocolHandler")

    // Tags already reported by [warnOnUnknownTag]. A tag on every line warns once, not thousands
    // of times.
    private val unknownReported = mutableSetOf<String>()

    /**
     * Warns the first time a tag arrives that the table does not account for at all. Every tag the
     * game is known to send is registered above, the ones we do nothing with as
     * [IgnoredTagHandler], so reaching here means either the protocol has grown something new or a
     * spelling in the table is wrong - and since tag names are matched exactly, a single wrong
     * letter would otherwise be invisible. The message names the nearest spelling we do hold when
     * there is one, which is usually the whole diagnosis.
     */
    private fun warnOnUnknownTag(tagName: String) {
        if (!unknownReported.add(tagName)) return
        val miscased = elementListeners.keys.firstOrNull { it.equals(tagName, ignoreCase = true) }
        if (miscased != null) {
            logger.w { "Ignoring <$tagName>: the tag we handle is spelled <$miscased>. One of the two is wrong." }
        } else {
            logger.w { "Ignoring <$tagName>: no handler for it. Add one, or an IgnoredTagHandler if it carries nothing we use." }
        }
    }

    private val elementListeners: Map<String, ElementListener> =
        mapOf(
            // Keyed by the protocol's exact spelling, because the real client matches tag
            // names case-sensitively: <streamWindow> and <openDialog> are recognised, while
            // <streamwindow> and <opendialog> are ignored without even an error (verified in
            // utils/wrayth-lab). Every spelling below was read off captured DR/DRT/GS4
            // sessions rather than inferred from the handler class name, which is not a
            // reliable guide - UpdateVerbsHandler answers to <updateverbs>.
            "a" to AHandler(),
            "app" to AppHandler(),
            "b" to BHandler(),
            "background" to BackgroundHandler(),
            "castTime" to CastTimeHandler(),
            "clearContainer" to ClearContainerHandler(),
            "clearStream" to ClearStreamHandler(),
            "cli" to CliHandler(),
            "closeDialog" to CloseDialogHandler(),
            "cmdButton" to CmdButtonHandler(),
            "cmdlist" to CmdlistHandler(),
            "compass" to CompassHandler(),
            "compDef" to CompDefHandler(),
            "component" to ComponentHandler(),
            "container" to ContainerHandler(),
            "d" to DHandler(),
            "dialogData" to DialogDataHandler(),
            "dir" to DirHandler(),
            "dropDownBox" to DropDownBoxHandler(),
            // No captured session contains these two, so their exact spelling is unknown.
            // Both forms are registered rather than guessed at, because guessing wrong would
            // silently drop a tag the game does send. Collapse each to a single entry once one
            // shows up in a log.
            "dynastream" to DynaStreamHandler(),
            "dynaStream" to DynaStreamHandler(),
            "image" to ImageHandler(),
            "indicator" to IndicatorHandler(),
            "inv" to InvHandler(),
            "label" to LabelHandler(),
            "launchurl" to LaunchURLHandler(),
            "launchURL" to LaunchURLHandler(),
            "left" to LeftHandler(),
            "link" to LinkHandler(),
            "menu" to MenuHandler(),
            "menuImage" to MenuImageHandler(),
            "menuLink" to MenuLinkHandler(),
            "mi" to MiHandler(),
            "mode" to ModeHandler(),
            "nav" to NavHandler(),
            "openDialog" to OpenDialogHandler(),
            "output" to OutputHandler(),
            "preset" to PresetHandler(),
            "popBold" to PopBoldHandler(),
            "popStream" to PopStreamHandler(),
            "progressBar" to ProgressBarHandler(),
            "prompt" to PromptHandler(),
            "pushBold" to PushBoldHandler(),
            "pushStream" to PushStreamHandler(),
            "radio" to RadioHandler(),
            "resource" to ResourceHandler(),
            "right" to RightHandler(),
            "roundTime" to RoundTimeHandler(),
            "settingsInfo" to SettingsInfoHandler(),
            "skin" to SkinHandler(),
            "spell" to SpellHandler(),
            "stream" to StreamHandler(),
            "streamWindow" to StreamWindowHandler(),
            "style" to StyleHandler(),
            "updateverbs" to UpdateVerbsHandler(),
            // Tags the game sends that carry nothing we use. Registered so the warning above
            // stays meaningful: anything that reaches it is genuinely new, or misspelled. Every
            // name here was taken from captured DR/DRT/GS4 sessions.
            //
            // The bulk of them are the settings blob the server replays at login - <settings> and
            // its sections, down to the one-letter entries inside them (<i> palette, <k> macro key,
            // <w> window, <o> option, <p> preset, <m> misc, <s> script). Warlock keeps its own
            // settings, so none of it is read.
            "builtin" to IgnoredTagHandler(),
            "cmdline" to IgnoredTagHandler(),
            "detach" to IgnoredTagHandler(),
            "dialog" to IgnoredTagHandler(),
            "display" to IgnoredTagHandler(),
            "i" to IgnoredTagHandler(),
            "ignores" to IgnoredTagHandler(),
            "k" to IgnoredTagHandler(),
            "keys" to IgnoredTagHandler(),
            "m" to IgnoredTagHandler(),
            "macros" to IgnoredTagHandler(),
            "misc" to IgnoredTagHandler(),
            "names" to IgnoredTagHandler(),
            "o" to IgnoredTagHandler(),
            "options" to IgnoredTagHandler(),
            "p" to IgnoredTagHandler(),
            "palette" to IgnoredTagHandler(),
            "panels" to IgnoredTagHandler(),
            "presets" to IgnoredTagHandler(),
            "s" to IgnoredTagHandler(),
            "scripts" to IgnoredTagHandler(),
            "settings" to IgnoredTagHandler(),
            "strings" to IgnoredTagHandler(),
            "toggles" to IgnoredTagHandler(),
            "vars" to IgnoredTagHandler(),
            "w" to IgnoredTagHandler(),
            // The rest arrive during play.
            "closeButton" to IgnoredTagHandler(),
            "checkBox" to IgnoredTagHandler(),
            "cmdtimestamp" to IgnoredTagHandler(),
            "command" to IgnoredTagHandler(),
            "deleteContainer" to IgnoredTagHandler(),
            "endSetup" to IgnoredTagHandler(),
            "exposeContainer" to IgnoredTagHandler(),
            "exposeDialog" to IgnoredTagHandler(),
            "exposeStream" to IgnoredTagHandler(),
            "font" to IgnoredTagHandler(),
            "group" to IgnoredTagHandler(),
            "module" to IgnoredTagHandler(),
            "objectives" to IgnoredTagHandler(),
            "playerID" to IgnoredTagHandler(),
            "roommeta" to IgnoredTagHandler(),
            "sep" to IgnoredTagHandler(),
            "switchQuickBar" to IgnoredTagHandler(),
            "timestamp" to IgnoredTagHandler(),
            "upDownEditBox" to UpDownEditBoxHandler(),
        )

    // Reused across [parseLine] calls (see the note there). Error listeners are removed so malformed
    // game lines don't spam stderr - parseLine catches parse failures and emits a WraythParseErrorEvent.
    private val lexer = WraythLexer(CharStreams.fromString("")).apply { removeErrorListeners() }
    private val parser = WraythParser(CommonTokenStream(lexer)).apply { removeErrorListeners() }

    fun parseLine(line: String): List<WraythEvent> {
        return try {
            // Ignore lines with Wizard commands
            if (line.startsWith('\u001C')) {
                return emptyList()
            }
            // Reuse the lexer/parser (and their ATN simulators / DFA cache) across lines, resetting
            // them per call, to avoid per-line allocation. Safe because parseLine is only ever called
            // sequentially on a single connection's read loop.
            lexer.inputStream = CharStreams.fromString(line)
            lexer.reset()
            parser.tokenStream = CommonTokenStream(lexer)
            parser.reset()
            val contents = WraythNodeVisitor.visitDocument(parser.document())
            handleContent(contents)
        } catch (e: Exception) {
            logger.e(e) { "Error parsing line" }
            listOf(WraythParseErrorEvent(line))
        }
    }

    private fun handleContent(contents: List<Content>): List<WraythEvent> {
        // open/close tags must occur on the same line
        var lineHasTags = false
        val tagStack = ArrayDeque<OpenTag>()
        val events = mutableListOf<WraythEvent>()

        fun close(tag: OpenTag): WraythEvent? = elementListeners[tag.name]?.endElement(tag.element.attributes, tag.chars.toString())

        for (content in contents) {
            when (content) {
                is StartElement -> {
                    lineHasTags = true
                    val tagName = content.name
                    tagStack.addFirst(OpenTag(tagName, content))
                    val listener = elementListeners[tagName]
                    if (listener != null) {
                        listener.startElement(content)?.let {
                            events.add(it)
                        }
                    } else {
                        warnOnUnknownTag(tagName)
                        events.add(WraythUnhandledTagEvent(content.name))
                    }
                }

                is EndElement -> {
                    // This is ugly. It's inherently a bit complicated because we're working around protocol bugs.
                    // 0: <tag></tag> - all good! - <tag></tag>
                    // 1: </tag> - ignored
                    // 2: <foo></bar></foo> - </bar> is ignored: <foo></foo>
                    // 3: <foo><bar></foo> - <bar> is closed (ha!): <foo><bar></bar></foo>
                    // 4: <foo><bar></foo></bar> - first rule 3 is applied, then rule 1
                    // 5: <foo> - handled after the event loop: <foo></foo>
                    lineHasTags = true
                    val topOfStack =
                        tagStack.firstOrNull()
                            ?: continue // rule #1
                    val tagName = content.name
                    val matched: OpenTag
                    if (topOfStack.name != tagName) {
                        logger.e {
                            "Received end element ($tagName) does not match element on the top of the stack (${topOfStack.name})!"
                        }
                        if (tagStack.any { it.name == tagName }) {
                            while (tagName != tagStack.first().name) {
                                // close excess tags - rule #3
                                close(tagStack.removeFirst())?.let { events.add(it) }
                            }
                            matched = tagStack.first()
                        } else {
                            // ignore unmatched end tag - rule #2
                            continue
                        }
                    } else {
                        // remove the matched tag from the stack - rule #0
                        matched = tagStack.removeFirst()
                    }
                    // rules 0, and 3
                    close(matched)?.let { events.add(it) }
                }

                is CharData -> {
                    var charEvent: WraythEvent? = null

                    // Go through the stack until someone handles these characters,
                    //   otherwise use the default handler
                    for (tag in tagStack) {
                        val event = elementListeners[tag.name]?.characters(content.data)
                        if (event != null) {
                            charEvent = event
                            // Accumulate the consumed text so the tag's endElement can use it.
                            tag.chars.append(content.data)
                            break
                        }
                    }
                    if (charEvent != null) {
                        events.add(charEvent)
                    } else {
                        events.add(WraythDataReceivedEvent(content.data))
                    }
                }
            }
        }
        // Close remaining open tags - rule #5
        while (tagStack.isNotEmpty()) {
            close(tagStack.removeFirst())?.let { events.add(it) }
        }
        // If a line has tags, ignore it when it has no text
        events.add(WraythEolEvent(ignoreWhenBlank = lineHasTags))
        return events
    }
}

interface ElementListener {
    fun startElement(element: StartElement): WraythEvent?

    fun characters(data: String): WraythEvent?

    fun endElement(): WraythEvent?

    /**
     * Called when the element closes, with the element's [attributes] and the character [text]
     * accumulated inside it. Listeners that need that context (e.g. a command link) override this and
     * stay stateless; the rest inherit the default, which ignores both and falls back to [endElement].
     */
    fun endElement(
        attributes: Map<String, String>,
        text: String,
    ): WraythEvent? = endElement()
}

// The state needed to close an open tag, tracked by the handler (not the element listeners) so the
// shared listener instances stay stateless: the tag name, its start element (for attributes), and the
// character data accumulated inside it.
private class OpenTag(
    val name: String,
    val element: StartElement,
) {
    val chars = StringBuilder()
}

abstract class BaseElementListener : ElementListener {
    override fun startElement(element: StartElement): WraythEvent? = null

    override fun characters(data: String): WraythEvent? = null

    override fun endElement(): WraythEvent? = null
}

/**
 * A tag the game sends that we knowingly do nothing with. Registering it is what separates "we
 * looked at this and it carries nothing we use" from "we have never seen this before", which is the
 * only case [WraythProtocolHandler] warns about.
 */
class IgnoredTagHandler : BaseElementListener()
