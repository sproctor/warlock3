package warlockfe.warlock3.scripting.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StringHelpersTest {
    @Test
    fun parsesInteger() {
        assertEquals("123", "123".toBigDecimalOrNull()?.toPlainString())
    }

    @Test
    fun parsesDecimal() {
        assertEquals("3.14", "3.14".toBigDecimalOrNull()?.toPlainString())
    }

    @Test
    fun parsesNegative() {
        assertEquals("-7", "-7".toBigDecimalOrNull()?.toPlainString())
    }

    @Test
    fun nonNumericReturnsNull() {
        assertNull("hello".toBigDecimalOrNull())
    }

    @Test
    fun emptyReturnsNull() {
        assertNull("".toBigDecimalOrNull())
    }
}
