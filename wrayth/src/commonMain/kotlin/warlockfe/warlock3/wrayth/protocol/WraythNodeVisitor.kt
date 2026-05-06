package warlockfe.warlock3.wrayth.protocol

import warlockfe.warlock3.wrayth.parsers.generated.WraythParser
import warlockfe.warlock3.wrayth.parsers.generated.WraythParserBaseVisitor
import warlockfe.warlock3.wrayth.util.unescapeXml

object WraythNodeVisitor : WraythParserBaseVisitor<List<Content>>() {
    override fun visitDocument(ctx: WraythParser.DocumentContext): List<Content> = visit(ctx.content())

    override fun visitContent(ctx: WraythParser.ContentContext): List<Content> {
        if (ctx.children == null) {
            return emptyList()
        }
        val result = mutableListOf<Content>()
        for (child in ctx.children) {
            val content = visit(child)
            result.addAll(content)
        }
        return result
    }

    override fun visitStartTag(ctx: WraythParser.StartTagContext): List<Content> =
        listOf(StartElement(ctx.Name().text, getAttributes(ctx.attribute())))

    override fun visitEndTag(ctx: WraythParser.EndTagContext): List<Content> = listOf(EndElement(ctx.Name().text))

    override fun visitEmptyTag(ctx: WraythParser.EmptyTagContext): List<Content> {
        val name = ctx.Name().text
        return listOf(StartElement(name, getAttributes(ctx.attribute())), EndElement(name))
    }

    override fun visitChardata(ctx: WraythParser.ChardataContext): List<Content> = listOf(CharData(ctx.TEXT().text))

    override fun visitReference(ctx: WraythParser.ReferenceContext): List<Content> {
        val ref = ctx.EntityRef()?.text ?: ctx.CharRef()?.text ?: return emptyList()
        return listOf(CharData(unescapeXml(ref)))
    }

    private fun getAttributes(contextList: List<WraythParser.AttributeContext>): Map<String, String> {
        val attributes = HashMap<String, String>()
        for (attributeContext in contextList) {
            val name = attributeContext.Name().text
            // remove ' or " from start and end of value. If value is empty, use name as value
            val value =
                attributeContext
                    .STRING()
                    ?.text
                    ?.drop(1)
                    ?.dropLast(1)
                    ?.let { unescapeXml(it) }
            attributes[name] = value ?: name
        }
        return attributes
    }

    override fun defaultResult(): List<Content> = emptyList()
}

sealed class Content

data class StartElement(
    val name: String,
    val attributes: Map<String, String>,
) : Content()

data class EndElement(
    val name: String,
) : Content()

data class CharData(
    val data: String,
) : Content()
