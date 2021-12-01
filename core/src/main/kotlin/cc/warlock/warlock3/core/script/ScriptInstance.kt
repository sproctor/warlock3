package cc.warlock.warlock3.core.script

import cc.warlock.warlock3.core.client.WarlockClient

interface ScriptInstance {
    val name: String
    val isRunning: Boolean
    val isSuspended: Boolean

    fun start(client: WarlockClient, argumentString: String, onStop: () -> Unit)

    fun stop()

    fun suspend()

    fun resume()

}