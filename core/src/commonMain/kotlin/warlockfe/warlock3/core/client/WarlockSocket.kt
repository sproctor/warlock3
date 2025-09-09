package warlockfe.warlock3.core.client

interface WarlockSocket {

    val isClosed: Boolean

    suspend fun readLine(): String?
    suspend fun read(): Int
    fun ready(): Boolean
    suspend fun write(text: String)
    fun close()
}