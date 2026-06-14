package warlockfe.warlock3.core.sge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SalFileTest {
    @Test
    fun parsesHostPortAndKey() {
        val content =
            """
            GAME=STORM
            GAMECODE=DR
            FULLGAMENAME=DragonRealms
            GAMEHOST=storm.gs4.game.play.net
            GAMEPORT=10024
            KEY=abc123def456
            """.trimIndent()

        val credentials = parseSalCredentials(content)

        assertEquals("storm.gs4.game.play.net", credentials.host)
        assertEquals(10024, credentials.port)
        assertEquals("abc123def456", credentials.key)
    }

    @Test
    fun ignoresWhitespaceAndCasing() {
        val content =
            """
              gamehost = play.net
            GamePort=4901
            key=  secret-key
            """.trimIndent()

        val credentials = parseSalCredentials(content)

        assertEquals("play.net", credentials.host)
        assertEquals(4901, credentials.port)
        assertEquals("secret-key", credentials.key)
    }

    @Test
    fun failsWhenKeyMissing() {
        val content =
            """
            GAMEHOST=play.net
            GAMEPORT=4901
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> { parseSalCredentials(content) }
    }

    @Test
    fun failsWhenPortInvalid() {
        val content =
            """
            GAMEHOST=play.net
            GAMEPORT=notaport
            KEY=secret
            """.trimIndent()

        assertFailsWith<IllegalArgumentException> { parseSalCredentials(content) }
    }
}
