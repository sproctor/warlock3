package warlockfe.warlock3.core.client

/** See [WarlockClient.addLineInterceptor]. */
interface LineInterceptor {
    /** Whether to take [text], a line of game text, so it is neither shown nor passed on. */
    fun intercept(text: String): Boolean

    /** A prompt arrived: the game has finished answering a command. */
    fun prompt() {}
}
