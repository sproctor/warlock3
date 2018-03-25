package cc.warlock.warlock3.stormfront.parser

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parser
import com.github.h0tk3y.betterParse.parser.Parser
import com.github.h0tk3y.betterParse.parser.parse

class StormfrontParser() : Grammar<List<StormfrontAst>>() {
    val tagEndOpen by token("</")
    val tagStartOpen by token("<")
    val tagClose by token(">")
    val tagEmptyClose by token("/>")
    val name by token("[\\w_][\\w\\d.\\-_]*")
    val eq by token("=")
    val singleQuote by token("'")
    val doubleQuote by token("\"")
    val singleQuoteContent by singleQuote * zeroOrMore(token("[^\\\\']") or token("\\\\'")
            or token("\\\\")) * singleQuote
    val doubleQuoteContent by doubleQuote * zeroOrMore(token("[^\\\\\"]") or token("\\\\\"")
            or token("\\\\")) * doubleQuote
    val attributeContent by singleQuoteContent or doubleQuoteContent
    val attribute: Parser<Attribute> by name * optional(eq * attributeContent) map {
        Attribute(name.toString(), "")
    }
    val spaces by token("[ \\t]+")
    val attributes: Parser<List<Attribute>> by separatedTerms(attribute, spaces, true)

    val tagStart: Parser<List<StormfrontAst>> by tagStartOpen * name * optional(skip(spaces) * attributes) * skip(tagClose) map {
        listOf(Tag(it.t1.text, it.t3))
    }
    val tagEmpty: Parser<List<StormfrontAst>> by tagStartOpen * name * optional(skip(spaces) * attributes) * skip(tagEmptyClose) map {
        listOf(Tag(it.t1.text, it.t3), EndTag(it.t1.text))
    }
    val tagEnd: Parser<List<StormfrontAst>> by tagEndOpen * name * skip(tagClose) map {
        listOf(EndTag(it.t2.text))
    }
    val entity by token("&#?[\\w\\d]+;")
    val normalChars by token("\\w+")
    val amp by token("&")
    val allowableLt by token("<[^\\w_]")
    val content: Parser<List<StormfrontAst>> by oneOrMore(normalChars or entity
            or (amp map { it.text }) or (allowableLt map { it.text })) map {
        listOf(Content(it.joinToString(separator = "")))
    }
    override val rootParser: Parser<List<StormfrontAst>> by zeroOrMore(tagStart or tagEmpty or tagEnd or content) map {
        it.flatten()
    }
}

sealed class StormfrontAst

data class Tag(val name: String, val attributes: List<Attribute>?) : StormfrontAst()
data class EndTag(val name: String) : StormfrontAst()
data class Content(val text: String) : StormfrontAst()

data class Attribute(val key: String, val value: String)