package warlockfe.warlock3.core.client

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import java.net.Socket

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
    var quoteChar = ' '
    var escaped = false

    for (ch in command) {
        when {
            escaped -> {
                current.append(ch)
                escaped = false
            }
            ch == '\\' -> escaped = true
            inQuotes && ch == quoteChar -> inQuotes = false
            !inQuotes && (ch == '"' || ch == '\'') -> {
                inQuotes = true
                quoteChar = ch
            }
            !inQuotes && ch == ' ' -> {
                if (current.isNotEmpty()) {
                    args.add(current.toString())
                    current.clear()
                }
            }
            else -> current.append(ch)
        }
    }
    if (current.isNotEmpty()) args.add(current.toString())
    return args
}
