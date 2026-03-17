package warlockfe.warlock3.core.client

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

class JavaProxy(command: String) : WarlockProxy {
    private val process = ProcessBuilder(splitCommand(command)).start()

    override val isAlive: Boolean
        get() = process.isAlive
    override val stdOut: Flow<String>
        get() = process.inputStream.bufferedReader().lineSequence().asFlow()
    override val stdErr: Flow<String>
        get() = process.errorStream.bufferedReader().lineSequence().asFlow()

    override fun close() {
        process.destroy()
    }
}

private fun splitCommand(command: String): List<String> {
    val args = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var i = 0

    while (i < command.length) {
        val ch = command[i]
        // Count run of backslashes
        if (ch == '\\') {
            var numBackslashes = 0
            while (i < command.length && command[i] == '\\') {
                numBackslashes++
                i++
            }
            if (i < command.length && command[i] == '"') {
                // Backslashes before a quote: 2n -> n literal backslashes + quote toggles
                // 2n+1 -> n literal backslashes + literal quote
                current.append("\\".repeat(numBackslashes / 2))
                if (numBackslashes % 2 == 1) {
                    current.append('"')
                } else {
                    inQuotes = !inQuotes
                }
                i++ // consume the quote
            } else {
                // Backslashes not before a quote: all literal
                current.append("\\".repeat(numBackslashes))
            }
        } else if (ch == '"') {
            inQuotes = !inQuotes
            i++
        } else if (!inQuotes && (ch == ' ' || ch == '\t')) {
            if (current.isNotEmpty()) {
                args.add(current.toString())
                current.clear()
            }
            i++
        } else {
            current.append(ch)
            i++
        }
    }

    if (current.isNotEmpty()) args.add(current.toString())
    return args
}
