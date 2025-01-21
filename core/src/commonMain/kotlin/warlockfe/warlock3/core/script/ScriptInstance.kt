package warlockfe.warlock3.core.script

import warlockfe.warlock3.core.client.WarlockClient

interface ScriptInstance {
    val name: String
    val status: ScriptStatus

    fun start(client: WarlockClient, argumentString: String, onStop: () -> Unit)

    fun stop()

    fun suspend()

    fun resume()

}
