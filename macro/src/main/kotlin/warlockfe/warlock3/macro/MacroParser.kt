package warlockfe.warlock3.macro

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.Token
import warlockfe.warlock3.core.macro.MacroToken
import warlockfe.warlock3.macro.parser.MacroLexer

fun parseMacro(text: String): List<MacroToken>? {
    return tokenizeMacro(text)?.map { token ->
        when (token.type) {
            MacroLexer.Entity -> {
                val entity = token.text
                assert(entity.length == 2)
                assert(entity[0] == '\\')
                MacroToken.Entity(entity[1])
            }

            MacroLexer.At -> {
                MacroToken.At
            }

            MacroLexer.Question -> {
                MacroToken.Question
            }

            MacroLexer.Character -> {
                MacroToken.Text(token.text)
            }

            MacroLexer.VariableName -> {
                token.text?.let { if (it.endsWith("%")) it.drop(1) else it }
                    .let { name ->
                        MacroToken.Variable(name ?: "")
                    }
            }

            MacroLexer.CommandText -> {
                MacroToken.Command(token.text.lowercase())
            }

            else -> error("Unexpected token: ${token.type}")
        }
    }
}

private fun tokenizeMacro(input: String): List<Token>? {
    try {
        val charStream = CharStreams.fromString(input)
        val lexer = MacroLexer(charStream)
        return lexer.allTokens
    } catch (_: Exception) {
        return null // catch lex issues
    }
}