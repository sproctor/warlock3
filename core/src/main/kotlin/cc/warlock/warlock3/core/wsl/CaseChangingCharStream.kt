package cc.warlock.warlock3.core.wsl

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.misc.Interval


/**
 * This class supports case-insensitive lexing by wrapping an existing
 * [CharStream] and forcing the lexer to see either upper or
 * lowercase characters. Grammar literals should then be either upper or
 * lower case such as 'BEGIN' or 'begin'. The text of the character
 * stream is unaffected. Example: input 'BeGiN' would match lexer rule
 * 'BEGIN' if constructor parameter upper=true but getText() would return
 * 'BeGiN'.
 */
class CaseChangingCharStream(private val stream: CharStream) : CharStream {

    override fun getText(interval: Interval?): String {
        return stream.getText(interval)
    }

    override fun consume() {
        stream.consume()
    }

    override fun LA(i: Int): Int {
        val c = stream.LA(i)
        if (c <= 0) {
            return c
        }
        return Character.toLowerCase(c)
    }

    override fun mark(): Int {
        return stream.mark()
    }

    override fun release(marker: Int) {
        stream.release(marker)
    }

    override fun index(): Int {
        return stream.index()
    }

    override fun seek(index: Int) {
        stream.seek(index)
    }

    override fun size(): Int {
        return stream.size()
    }

    override fun getSourceName(): String {
        return stream.getSourceName()
    }
}