package warlockfe.warlock3.scripting.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StringHelpersTest {
    @Test
    fun parsesInteger() {
        assertEquals(123.0, "123".toDoubleOrNull())
    }

    @Test
    fun parsesDecimal() {
        assertEquals(3.14, "3.14".toDoubleOrNull())
    }

    @Test
    fun parsesNegative() {
        assertEquals(-7.0, "-7".toDoubleOrNull())
    }

    @Test
    fun nonNumericReturnsNull() {
        assertNull("hello".toDoubleOrNull())
    }

    @Test
    fun emptyReturnsNull() {
        assertNull("".toDoubleOrNull())
    }
}
