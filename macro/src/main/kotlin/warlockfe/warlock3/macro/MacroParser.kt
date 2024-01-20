package warlockfe.warlock3.macro

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.Token
import warlockfe.warlock3.core.macro.MacroToken
import warlockfe.warlock3.macro.parser.MacroLexer

fun parseMacro(text: String): Result<List<MacroToken>> =
    tokenizeMacro(text).map { tokens ->
        val result = mutableListOf<MacroToken>()
        tokens.forEach { token ->
            when (token.type) {
                MacroLexer.Entity -> {
                    val entity = token.text
                    assert(entity.length == 2)
                    assert(entity[0] == '\\')
                    result.add(MacroToken.Entity(entity[1]))
                }

                MacroLexer.At -> {
                    result.add(MacroToken.At)
                }

                MacroLexer.Question -> {
                    result.add(MacroToken.Question)
                }

                MacroLexer.Character -> {
                    result.add(MacroToken.Text(token.text))
                }

                MacroLexer.VariableName -> {
                    token.text?.let { if (it.endsWith("%")) it.drop(1) else it }
                        ?.let { name ->
                            result.add(MacroToken.Variable(name))
                        }
                }

                MacroLexer.CommandText -> {
                    result.add(MacroToken.Command(token.text.lowercase()))
                }
            }
        }
        result
    }

private fun tokenizeMacro(input: String): Result<List<Token>> =
    runCatching {
        val charStream = CharStreams.fromString(input)
        val lexer = MacroLexer(charStream)
        lexer.allTokens
    }