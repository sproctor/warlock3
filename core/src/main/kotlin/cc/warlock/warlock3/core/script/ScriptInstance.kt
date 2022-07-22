package cc.warlock.warlock3.core.script

import cc.warlock.warlock3.core.client.WarlockClient
import java.util.UUID

interface ScriptInstance {
    val id: UUID
    val name: String
    val status: ScriptStatus

    fun start(client: WarlockClient, argumentString: String, onStop: () -> Unit)

    fun stop()

    fun suspend()

    fun resume()

}

enum class ScriptStatus {
    NotStarted,
    Running,
    Suspended,
    Stopped;

    override fun toString(): String {
        return when (this) {
            NotStarted -> "not started"
            Running -> "running"
            Suspended -> "paused"
            Stopped -> "stopped"
        }
    }
}