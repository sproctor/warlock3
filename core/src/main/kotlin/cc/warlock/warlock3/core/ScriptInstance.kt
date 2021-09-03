package cc.warlock.warlock3.core

interface ScriptInstance {
    val name: String
    val isRunning: Boolean

    fun start(client: WarlockClient, arguments: List<String>)

    fun stop()

    fun suspend()

    fun resume()

}