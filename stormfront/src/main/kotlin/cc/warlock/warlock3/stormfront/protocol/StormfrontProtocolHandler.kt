package cc.warlock.warlock3.stormfront.protocol

import cc.warlock.warlock3.core.StyledString
import cc.warlock.warlock3.core.WarlockStyle
import cc.warlock.warlock3.stormfront.parser.StormfrontLexer
import cc.warlock.warlock3.stormfront.parser.StormfrontParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.util.*
import kotlin.collections.HashMap

class StormfrontProtocolHandler {
    private val elementStack = LinkedList<StartElement>()
    private val elementListeners = HashMap<String, LinkedList<ElementListener>>()
    private val dataListeners = LinkedList<DataListener>()
    private val timeListeners = LinkedList<TimeListener>()
    private val promptListeners = LinkedList<PromptListener>()

    private var outputStyle: WarlockStyle? = null

    // FIXME: this is kind of hacky
    private var lineHasTags = false
    private var lineHasText = false

    init {
        addElementListener("prompt", object : ElementListener {
            // the following is undefined: <prompt> <prompt>foo</prompt> bar </prompt>
            var prompt = StringBuilder()
            override fun startElement(element: StartElement) {
                prompt.setLength(0)

                val time = element.attributes["time"]?.toLong()
                if (time != null) {
                    timeListeners.forEach { it.syncTime(time) }
                }
            }
            override fun characters(data: String): Boolean {
                prompt.append(data)
                return true
            }
            override fun endElement(element: EndElement) {
                promptListeners.forEach { it.prompt(prompt.toString()) }
                if (elementStack.isNotEmpty()) {
                    println("PARSE ERROR elements on stack during prompt!!!")
                }
            }
        })

        addElementListener("output", object : BaseElementListener() {
            override fun startElement(element: StartElement) {
                val className = element.attributes["class"]
                outputStyle = getStyleByClass(className)
            }
        })
    }

    fun parseLine(line: String) {
        val inputStream = CharStreams.fromString(line)
        val lexer = StormfrontLexer(inputStream)
        val tokens = CommonTokenStream(lexer)
        val parser = StormfrontParser(tokens)
        val contents = StormfrontNodeVisitor.visitDocument(parser.document())
        handleContent(contents)
    }

    private fun handleContent(contents: List<Content>) {
        for (content in contents) {
            when (content) {
                is StartElement -> {
                    lineHasTags = true
                    elementStack.push(content)
                    elementListeners[content.name]?.forEach { it.startElement(content) }
                }
                is EndElement -> {
                    lineHasTags = true
                    if (elementStack.pop().name != content.name) {
                        println("ERROR: Received end element does not match element on the top of the stack!")
                    }
                    elementListeners[content.name]?.forEach { it.endElement(content) }
                }
                is CharData -> {
                    val name = elementStack.peek()?.name

                    // call the character handlers on the CharData
                    // if none returned true (handled) then call the global handlers
                    if (elementListeners[name]
                            ?.map { it.characters(content.data) }
                            ?.reduce { a, b -> a or b } != true) {
                        val string = StyledString(content.data, outputStyle)
                        lineHasText = true
                        dataListeners.forEach { it.characters(string) }
                    }
                }
            }
        }
        // If a line has just tags, don't send the newline, otherwise do.
        if (lineHasText || !lineHasTags) {
            //val string = StyledString("\n", outputStyle)
            //dataListeners.forEach { it.characters(string) }
            dataListeners.forEach { it.eol() }
        }
        lineHasTags = false
        lineHasText = false
    }

    fun addDataListener(listener: DataListener) {
        dataListeners.add(listener)
    }

    fun addElementListener(name: String, listener: ElementListener) {
        elementListeners
            .getOrPut(name) { LinkedList() }
            .add(listener)
    }

    fun getStyleByClass(name: String?): WarlockStyle? {
        return when (name) {
            "mono" -> {
                WarlockStyle(monospace = true)
            }
            else -> null
        }
    }
}

interface ElementListener {
    fun startElement(element: StartElement)
    fun characters(data: String): Boolean
    fun endElement(element: EndElement)
}

abstract class BaseElementListener : ElementListener{
    override fun startElement(element: StartElement) { }
    override fun characters(data: String): Boolean { return false }
    override fun endElement(element: EndElement) { }
}

interface DataListener {
    // got come CharData or entity reference that were not handled by an element listener
    fun characters(text: StyledString)
    // finished current line
    fun eol()
}

interface TimeListener {
    fun syncTime(time: Long)
}

interface PromptListener {
    fun prompt(prompt: String)
}