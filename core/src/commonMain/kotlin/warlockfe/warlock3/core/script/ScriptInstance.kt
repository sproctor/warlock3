package warlockfe.warlock3.core.script

import warlockfe.warlock3.core.client.WarlockClient
import warlockfe.warlock3.core.script.ScriptStatus

interface ScriptInstance {
    val id: Long
    val name: String
    val status: ScriptStatus

    fun start(client: WarlockClient, argumentString: String, onStop: () -> Unit)

    fun stop()

    fun suspend()

    fun resume()

}
