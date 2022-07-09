package cc.warlock.warlock3.stormfront.protocol

import cc.warlock.warlock3.stormfront.parser.StormfrontParser
import cc.warlock.warlock3.stormfront.parser.StormfrontParserBaseVisitor
import org.apache.commons.text.StringEscapeUtils
import java.util.*

object StormfrontNodeVisitor : StormfrontParserBaseVisitor<List<Content>>() {
    override fun visitDocument(ctx: StormfrontParser.DocumentContext?): List<Content> {
        return ctx?.let { visit(it.content()) } ?: emptyList()
    }

    override fun visitContent(ctx: StormfrontParser.ContentContext?): List<Content> {
        if (ctx?.children == null) {
            return emptyList()
        }
        val result = LinkedList<Content>()
        for (child in ctx.children) {
            result.addAll(visit(child))
        }
        return result
    }

    override fun visitStartTag(ctx: StormfrontParser.StartTagContext): List<Content> {
        return listOf(StartElement(ctx.Name().text, getAttributes(ctx.attribute())))
    }

    override fun visitEndTag(ctx: StormfrontParser.EndTagContext): List<Content> {
        return listOf(EndElement(ctx.Name().text))
    }

    override fun visitEmptyTag(ctx: StormfrontParser.EmptyTagContext): List<Content> {
        val name = ctx.Name().text
        return listOf(StartElement(name, getAttributes(ctx.attribute())), EndElement(name))
    }

    override fun visitChardata(ctx: StormfrontParser.ChardataContext): List<Content> {
        return listOf(CharData(ctx.TEXT().text))
    }

    override fun visitReference(ctx: StormfrontParser.ReferenceContext): List<Content> {
        val ref = ctx.EntityRef()?.text ?: ctx.CharRef().text
        return listOf(CharData(StringEscapeUtils.unescapeXml(ref)))
    }

    private fun getAttributes(contextList: List<StormfrontParser.AttributeContext>): Map<String, String> {
        val attributes = HashMap<String, String>()
        for (attributeContext in contextList) {
            val name = attributeContext.Name().text
            // remove ' or " from start and end of value. If value is empty, use name as value
            val value = attributeContext.STRING()?.text?.drop(1)?.dropLast(1) ?: name
            attributes[name] = value
        }
        return attributes
    }
}

sealed class Content
data class StartElement(val name: String, val attributes: Map<String, String>) : Content()
data class EndElement(val name: String) : Content()
data class CharData(val data: String) : Content()