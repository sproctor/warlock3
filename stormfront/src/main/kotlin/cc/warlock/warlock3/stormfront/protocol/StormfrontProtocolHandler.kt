package cc.warlock.warlock3.stormfront.protocol

import cc.warlock.warlock3.stormfront.parser.StormfrontLexer
import cc.warlock.warlock3.stormfront.parser.StormfrontParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.util.*

class StormfrontProtocolHandler {
    private val elementStack = LinkedList<StartElement>()
    private val elementListeners = LinkedList<ElementListener>()
    private val dataListeners = LinkedList<DataListener>()

    fun parseLine(line: String) {
        val inputStream = CharStreams.fromString(line)
        val lexer = StormfrontLexer(inputStream)
        val tokens = CommonTokenStream(lexer)
        val parser = StormfrontParser(tokens)
        val contents = StormfrontNodeVisitor.visitDocument(parser.document())
        handleContent(contents)
    }

    fun handleContent(contents: List<Content>) {
        for (content in contents) {
            when (content) {
                is StartElement -> {
                    elementStack.push(content)
                    elementListeners.forEach { it.startElement(content) }
                }
                is EndElement -> {
                    if (elementStack.pop().name != content.name) {
                        println("ERROR: Received end element does not match element on the top of the stack!")
                    }
                    elementListeners.forEach { it.endElement(content) }
                }
                is CharData -> dataListeners.forEach { it.characters(content.data) }
            }
        }
        dataListeners.forEach { it.done() }
    }

    fun addDataListener(listener: DataListener) {
        dataListeners.add(listener)
    }

    fun addElementListener(listener: ElementListener) {
        elementListeners.add(listener)
    }
}

interface ElementListener {
    fun startElement(element: StartElement)
    fun endElement(element: EndElement)
}

interface DataListener {
    // got come CharData or entity reference
    fun characters(data: String)
    // finished current line
    fun done()
}