package warlockfe.warlock3.core.client

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

class JavaProxy(command: String) : WarlockProxy {
    // TODO: manually split args respecting quotes. exec(String) is deprecated in Java 18+, use exec(Array<String>)
    private val process = Runtime.getRuntime().exec(command)

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
