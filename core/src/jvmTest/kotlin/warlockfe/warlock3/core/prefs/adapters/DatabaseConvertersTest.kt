package warlockfe.warlock3.core.prefs.adapters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class DatabaseConvertersTest {
    private val converters = DatabaseConverters()

    @Test
    fun `round-trips a uuid stored as 16 binary bytes`() {
        val uuid = Uuid.parse("48856eb8-99a1-48a6-ab7f-0f6692a10000")
        val stored = converters.fromUUID(uuid)
        assertEquals(16, stored.size)
        assertEquals(uuid, converters.toUuid(stored))
    }

    @Test
    fun `reads a legacy uuid stored as its 36-char string`() {
        // Old rows kept the UUID in textual form; SQLite hands those back as their UTF-8 bytes,
        // which is what crashed production before toUuid learned to parse the string form.
        val text = "48856eb8-99a1-48a6-ab7f-0f6692a10000"
        val stored = text.encodeToByteArray()
        assertEquals(36, stored.size)
        assertEquals(Uuid.parse(text), converters.toUuid(stored))
    }
}
