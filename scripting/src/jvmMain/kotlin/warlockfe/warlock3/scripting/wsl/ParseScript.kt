package warlockfe.warlock3.scripting.wsl

import org.antlr.v4.kotlinruntime.CharStream
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import warlockfe.warlock3.scripting.parsers.generated.WslLexer
import warlockfe.warlock3.scripting.parsers.generated.WslParser
import java.io.File

actual fun parseWslScript(script: File): WslParser.ScriptContext {
    val input: CharStream = CharStreams.fromStream(script.inputStream())
    val lexer = WslLexer(input)
    val parser = WslParser(CommonTokenStream(lexer))
    return parser.script()
}