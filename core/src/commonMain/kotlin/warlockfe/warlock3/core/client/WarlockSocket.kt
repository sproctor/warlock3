package warlockfe.warlock3.core.client

interface WarlockSocket {
    val isClosed: Boolean

    suspend fun connect(
        host: String,
        port: Int,
    )

    suspend fun readLine(): String?

    // Null once the peer has gone and there is nothing left to read, as with [readLine]. The end
    // of a connection is where every connection is headed, not an error, so it is a return value.
    suspend fun readAvailable(min: Int = 1): String?

    fun ready(): Boolean

    suspend fun write(text: String)

    fun close()
}
