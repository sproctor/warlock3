package cc.warlock.warlock3.stormfront.protocol

import cc.warlock.warlock3.stormfront.parser.StormfrontLexer
import cc.warlock.warlock3.stormfront.parser.StormfrontParser
import cc.warlock.warlock3.stormfront.protocol.elements.*
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.util.*

class StormfrontProtocolHandler {
    private val elementListeners: Map<String, ElementListener> = mapOf(
        // all keys must be lowercase
        "a" to AHandler(),
        "app" to AppHandler(),
        "b" to BHandler(),
        "casttime" to CastTimeHandler(),
        "compass" to CompassHandler(),
        "compdef" to CompDefHandler(),
        "component" to ComponentHandler(),
        "d" to DHandler(),
        "dialogdata" to DialogDataHandler(),
        "dir" to DirHandler(),
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
            println("Encountered a parse error")
            emptyList()
        }
    }

    private fun handleContent(contents: List<Content>): List<StormfrontEvent> {
        // open/close tags must occur on the same line
        var lineHasTags = false
        val elementStack = LinkedList<StartElement>()
        val events = LinkedList<StormfrontEvent>()
        for (content in contents) {
            when (content) {
                is StartElement -> {
                    lineHasTags = true
                    elementStack.push(content)
                    elementListeners[content.name.lowercase()]?.startElement(content)?.let {
                        events.add(it)
                    }
                }
                is EndElement -> {
                    lineHasTags = true
                    val topOfStack = elementStack.pop()
                    if (!topOfStack.name.equals(content.name, true)) {
                        println("ERROR: Received end element (${content.name}) does not match element on the top of the stack ($topOfStack)!")
                    }
                    elementListeners[content.name.lowercase()]?.endElement(content)?.let {
                        events.add(it)
                    }
                }
                is CharData -> {
                    var charEvent: StormfrontEvent? = null

                    // Go through the stack until someone handles these characters,
                    //   otherwise use the default handler
                    for (element in elementStack) {
                        val event = elementListeners[element.name.lowercase()]?.characters(content.data)
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
        // If a line has tags, ignore it when it has no text
        events.add(StormfrontEolEvent(ignoreWhenBlank = lineHasTags))
        return events
    }
}

interface ElementListener {
    fun startElement(element: StartElement): StormfrontEvent?
    fun characters(data: String): StormfrontEvent?
    fun endElement(element: EndElement): StormfrontEvent?
}

abstract class BaseElementListener : ElementListener {
    override fun startElement(element: StartElement): StormfrontEvent? = null
    override fun characters(data: String): StormfrontEvent? = null
    override fun endElement(element: EndElement): StormfrontEvent? = null
}
