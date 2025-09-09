package warlockfe.warlock3.core.macro

import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.Token
import warlockfe.warlock3.core.parsers.generated.MacroLexer

fun parseMacro(text: String): List<MacroToken>? {
    return tokenizeMacro(text)?.map { token ->
        when (token.type) {
            MacroLexer.Tokens.Entity -> {
                val entity = token.text!!
                require(entity.length == 2)
                require(entity[0] == '\\')
                MacroToken.Entity(entity[1])
            }

            MacroLexer.Tokens.At -> {
                MacroToken.At
            }

            MacroLexer.Tokens.Character -> {
                MacroToken.Text(token.text!!)
            }

            MacroLexer.Tokens.VariableName -> {
                token.text?.let { if (it.endsWith("%")) it.drop(1) else it }
                    .let { name ->
                        MacroToken.Variable(name ?: "")
                    }
            }

            MacroLexer.Tokens.CommandText -> {
                MacroToken.Command(token.text!!.lowercase())
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
