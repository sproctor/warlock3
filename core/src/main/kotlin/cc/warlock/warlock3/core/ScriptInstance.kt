package cc.warlock.warlock3.core

interface ScriptInstance {
    val name: String
    val isRunning: Boolean

    fun start(client: WarlockClient, argumentString: String)

    fun stop()

    fun suspend()

    fun resume()

}