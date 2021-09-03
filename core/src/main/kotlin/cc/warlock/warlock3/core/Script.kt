package cc.warlock.warlock3.core

interface Script {
    val name: String
    val isRunning: Boolean

    fun start(arguments: List<String>)

    fun stop()

    fun suspend()

    fun resume()

}