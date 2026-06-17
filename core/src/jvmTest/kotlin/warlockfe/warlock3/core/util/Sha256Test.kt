package warlockfe.warlock3.core.util

import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256Test {
    @Test
    fun `hashes the empty string`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(""),
        )
    }

    @Test
    fun `hashes abc`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc"),
        )
    }

    @Test
    fun `hashes a longer message`() {
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            sha256Hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
    }

    @Test
    fun `hashes a typical game key`() {
        // A 32-hex key as EAccess returns; verifies the result is 64 lowercase hex chars.
        val key = "0123456789abcdef0123456789abcdef"
        val hash = sha256Hex(key)
        assertEquals(64, hash.length)
        assertEquals(hash, hash.lowercase())
    }
}
