package warlockfe.warlock3.core.script

import warlockfe.warlock3.core.client.SendCommandType
import warlockfe.warlock3.core.client.WarlockClient

interface ScriptInstance {

    val id: Long
    val name: String
    val status: ScriptStatus

    fun start(
        client: WarlockClient,
        argumentString: String,
        onStop: () -> Unit,
        commandHandler: suspend (String) -> SendCommandType,
    )

    fun stop()

    fun suspend()

    fun resume()

}
