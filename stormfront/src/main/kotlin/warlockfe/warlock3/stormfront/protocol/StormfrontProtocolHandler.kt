package warlockfe.warlock3.stormfront.protocol

import io.github.oshai.kotlinlogging.KotlinLogging
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import warlockfe.warlock3.stormfront.parser.StormfrontLexer
import warlockfe.warlock3.stormfront.parser.StormfrontParser
import warlockfe.warlock3.stormfront.protocol.elements.AHandler
import warlockfe.warlock3.stormfront.protocol.elements.AppHandler
import warlockfe.warlock3.stormfront.protocol.elements.BHandler
import warlockfe.warlock3.stormfront.protocol.elements.CastTimeHandler
import warlockfe.warlock3.stormfront.protocol.elements.ClearContainerHandler
import warlockfe.warlock3.stormfront.protocol.elements.ClearStreamHandler
import warlockfe.warlock3.stormfront.protocol.elements.CompDefHandler
import warlockfe.warlock3.stormfront.protocol.elements.CompassHandler
import warlockfe.warlock3.stormfront.protocol.elements.ComponentHandler
import warlockfe.warlock3.stormfront.protocol.elements.ContainerHandler
import warlockfe.warlock3.stormfront.protocol.elements.DHandler
import warlockfe.warlock3.stormfront.protocol.elements.DialogDataHandler
import warlockfe.warlock3.stormfront.protocol.elements.DirHandler
import warlockfe.warlock3.stormfront.protocol.elements.DynaStreamHandler
import warlockfe.warlock3.stormfront.protocol.elements.IndicatorHandler
import warlockfe.warlock3.stormfront.protocol.elements.InvHandler
import warlockfe.warlock3.stormfront.protocol.elements.LeftHandler
import warlockfe.warlock3.stormfront.protocol.elements.ModeHandler
import warlockfe.warlock3.stormfront.protocol.elements.NavHandler
import warlockfe.warlock3.stormfront.protocol.elements.OutputHandler
import warlockfe.warlock3.stormfront.protocol.elements.PopBoldHandler
import warlockfe.warlock3.stormfront.protocol.elements.PopStreamHandler
import warlockfe.warlock3.stormfront.protocol.elements.PresetHandler
import warlockfe.warlock3.stormfront.protocol.elements.ProgressBarHandler
import warlockfe.warlock3.stormfront.protocol.elements.PromptHandler
import warlockfe.warlock3.stormfront.protocol.elements.PushBoldHandler
import warlockfe.warlock3.stormfront.protocol.elements.PushStreamHandler
import warlockfe.warlock3.stormfront.protocol.elements.RightHandler
import warlockfe.warlock3.stormfront.protocol.elements.RoundTimeHandler
import warlockfe.warlock3.stormfront.protocol.elements.SettingsInfoHandler
import warlockfe.warlock3.stormfront.protocol.elements.SpellHandler
import warlockfe.warlock3.stormfront.protocol.elements.StreamWindowHandler
import warlockfe.warlock3.stormfront.protocol.elements.StyleHandler
import java.util.LinkedList

class StormfrontProtocolHandler {

    private val logger = KotlinLogging.logger {}

    private val elementListeners: Map<String, ElementListener> = mapOf(
        // all keys must be lowercase
        "a" to AHandler(),
        "app" to AppHandler(),
        "b" to BHandler(),
        "casttime" to CastTimeHandler(),
        "clearcontainer" to ClearContainerHandler(),
        "clearstream" to ClearStreamHandler(),
        "compass" to CompassHandler(),
        "compdef" to CompDefHandler(),
        "component" to ComponentHandler(),
        "container" to ContainerHandler(),
        "d" to DHandler(),
        "dialogdata" to DialogDataHandler(),
        "dir" to DirHandler(),
        "dynastream" to DynaStreamHandler(),
        "indicator" to IndicatorHandler(),
        "inv" to InvHandler(),
        "left" to LeftHandler(),
        "mode" to ModeHandler(),
        "nav" to NavHandler(),
        "output" to OutputHandler(),
        "preset" to PresetHandler(),
        "popbold" to PopBoldHandler(),
        "popstream" to PopStreamHandler(),
        "progressbar" to ProgressBarHandler(),
        "prompt" to PromptHandler(),
        "pushbold" to PushBoldHandler(),
        "pushstream" to PushStreamHandler(),
        "right" to RightHandler(),
        "roundtime" to RoundTimeHandler(),
        "settingsinfo" to SettingsInfoHandler(),
        "streamwindow" to StreamWindowHandler(),
        "spell" to SpellHandler(),
        "style" to StyleHandler(),
    )

    fun parseLine(line: String): List<StormfrontEvent> {
        return try {
            val inputStream = CharStreams.fromString(line)
            val lexer = StormfrontLexer(inputStream)
            val tokens = CommonTokenStream(lexer)
            val parser = StormfrontParser(tokens)
            val contents = StormfrontNodeVisitor.visitDocument(parser.document())
            handleContent(contents)
        } catch (e: Exception) {
            e.printStackTrace()
            listOf(StormfrontParseErrorEvent(line))
        }
    }

    private fun handleContent(contents: List<Content>): List<StormfrontEvent> {
        // open/close tags must occur on the same line
        var lineHasTags = false
        val tagStack = LinkedList<String>()
        val events = LinkedList<StormfrontEvent>()
        for (content in contents) {
            when (content) {
                is StartElement -> {
                    lineHasTags = true
                    val tagName = content.name.lowercase()
                    tagStack.push(tagName)
                    val listener = elementListeners[tagName]
                    if (listener != null) {
                        listener.startElement(content)?.let {
                            events.add(it)
                        }
                    } else {
                        events.add(StormfrontUnhandledTagEvent(content.name))
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
                    val topOfStack = tagStack.firstOrNull()
                        ?: continue // rule #1
                    val tagName = content.name.lowercase()
                    if (topOfStack != tagName) {
                        logger.error { "Received end element ($tagName) does not match element on the top of the stack ($topOfStack)!" }
                        if (tagStack.contains(tagName)) {
                            while (tagName != tagStack.first()) {
                                // close excess tags - rule #3
                                val unbalancedTag = tagStack.removeFirst()
                                elementListeners[unbalancedTag]?.endElement()?.let {
                                    events.add(it)
                                }
                            }
                        } else {
                            // ignore unmatched end tag - rule #2
                            continue
                        }
                    } else {
                        // remove the matched tag from the stack - rule #0
                        tagStack.removeFirst()
                    }
                    // rules 0, and 3
                    elementListeners[tagName]?.endElement()?.let {
                        events.add(it)
                    }
                }
                is CharData -> {
                    var charEvent: StormfrontEvent? = null

                    // Go through the stack until someone handles these characters,
                    //   otherwise use the default handler
                    for (tagName in tagStack) {
                        val event = elementListeners[tagName]?.characters(content.data)
                        if (event != null) {
                            charEvent = event
                            break
                        }
                    }
                    if (charEvent != null) {
                        events.add(charEvent)
                    } else {
                        events.add(StormfrontDataReceivedEvent(content.data))
                    }
                }
            }
        }
        // Close remaining open tags
        while (tagStack.isNotEmpty()) {
            val topOfStack = tagStack.removeFirst()
            elementListeners[topOfStack]?.endElement()
        }
        // If a line has tags, ignore it when it has no text
        events.add(StormfrontEolEvent(ignoreWhenBlank = lineHasTags))
        return events
    }
}

interface ElementListener {
    fun startElement(element: StartElement): StormfrontEvent?
    fun characters(data: String): StormfrontEvent?
    fun endElement(): StormfrontEvent?
}

abstract class BaseElementListener : ElementListener {
    override fun startElement(element: StartElement): StormfrontEvent? = null
    override fun characters(data: String): StormfrontEvent? = null
    override fun endElement(): StormfrontEvent? = null
}
