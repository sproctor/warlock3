package warlockfe.warlock3.core.client

interface WarlockSocket {

    val isClosed: Boolean

    suspend fun connect(host: String, port: Int)
    suspend fun readLine(): String?
    suspend fun readAvailable(min: Int = 1): String
    fun ready(): Boolean
    suspend fun write(text: String)
    fun close()
}
