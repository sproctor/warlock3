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

    private val elementListeners: Map<String, ElementListener> =
        mapOf(
            // all keys must be lowercase
            "a" to AHandler(),
            "app" to AppHandler(),
            "b" to BHandler(),
            "background" to BackgroundHandler(),
            "casttime" to CastTimeHandler(),
            "clearcontainer" to ClearContainerHandler(),
            "clearstream" to ClearStreamHandler(),
            "cli" to CliHandler(),
            "cmdbutton" to CmdButtonHandler(),
            "cmdlist" to CmdlistHandler(),
            "compass" to CompassHandler(),
            "compdef" to CompDefHandler(),
            "component" to ComponentHandler(),
            "container" to ContainerHandler(),
            "d" to DHandler(),
            "dialogdata" to DialogDataHandler(),
            "dir" to DirHandler(),
            "dropdownbox" to DropDownBoxHandler(),
            "dynastream" to DynaStreamHandler(),
            "image" to ImageHandler(),
            "indicator" to IndicatorHandler(),
            "inv" to InvHandler(),
            "label" to LabelHandler(),
            "launchurl" to LaunchURLHandler(),
            "left" to LeftHandler(),
            "link" to LinkHandler(),
            "menu" to MenuHandler(),
            "mi" to MiHandler(),
            "mode" to ModeHandler(),
            "nav" to NavHandler(),
            "opendialog" to OpenDialogHandler(),
            "output" to OutputHandler(),
            "preset" to PresetHandler(),
            "popbold" to PopBoldHandler(),
            "popstream" to PopStreamHandler(),
            "progressbar" to ProgressBarHandler(),
            "prompt" to PromptHandler(),
            "pushbold" to PushBoldHandler(),
            "pushstream" to PushStreamHandler(),
            "radio" to RadioHandler(),
            "resource" to ResourceHandler(),
            "right" to RightHandler(),
            "roundtime" to RoundTimeHandler(),
            "settingsinfo" to SettingsInfoHandler(),
            "skin" to SkinHandler(),
            "spell" to SpellHandler(),
            "stream" to StreamHandler(),
            "streamwindow" to StreamWindowHandler(),
            "style" to StyleHandler(),
            "updateverbs" to UpdateVerbsHandler(),
            "updowneditbox" to UpDownEditBoxHandler(),
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
                    val tagName = content.name.lowercase()
                    tagStack.addFirst(OpenTag(tagName, content))
                    val listener = elementListeners[tagName]
                    if (listener != null) {
                        listener.startElement(content)?.let {
                            events.add(it)
                        }
                    } else {
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
                    val tagName = content.name.lowercase()
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
