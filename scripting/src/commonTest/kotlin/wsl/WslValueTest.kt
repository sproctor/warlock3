package warlockfe.warlock3.scripting.wsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WslValueTest {
    // --- WslBoolean ---

    @Test
    fun boolean_basics() {
        val t = WslBoolean(true)
        assertTrue(t.isBoolean())
        assertFalse(t.isNumeric())
        assertFalse(t.isMap())
        assertFalse(t.isNull())
        assertTrue(t.toBoolean())
        assertEquals("true", t.toString())
        assertEquals("false", WslBoolean(false).toString())
    }

    @Test
    fun boolean_toNumber_throws() {
        assertFailsWith<WslRuntimeException> { WslBoolean(true).toNumber() }
    }

    @Test
    fun boolean_equality() {
        assertEquals(WslBoolean(true), WslBoolean(true))
        assertFalse(WslBoolean(true) == WslBoolean(false))
        // Boolean only equals another boolean
        assertFalse(WslBoolean(true).equals(WslString("true")))
    }

    // --- WslString ---

    @Test
    fun string_toBoolean_parsesTrueFalse() {
        assertTrue(WslString("true").toBoolean())
        assertTrue(WslString("TRUE").toBoolean())
        assertFalse(WslString("false").toBoolean())
        assertFalse(WslString("False").toBoolean())
    }

    @Test
    fun string_toBoolean_nonBooleanThrows() {
        assertFailsWith<WslRuntimeException> { WslString("yes").toBoolean() }
    }

    @Test
    fun string_toNumber() {
        assertEquals(42.0, WslString("42").toNumber())
        assertEquals(3.5, WslString("3.5").toNumber())
    }

    @Test
    fun string_blankIsZero() {
        assertEquals(0.0, WslString("   ").toNumber())
        assertEquals(0.0, WslString("").toNumber())
    }

    @Test
    fun string_nonNumericThrows() {
        assertFailsWith<WslRuntimeException> { WslString("hello").toNumber() }
    }

    @Test
    fun string_isNumeric() {
        assertTrue(WslString("10").isNumeric())
        assertTrue(WslString("-2.5").isNumeric())
        assertFalse(WslString("abc").isNumeric())
        assertFalse(WslString("").isNumeric())
    }

    @Test
    fun string_indexProperty() {
        val s = WslString("abc")
        assertEquals("a", s.getProperty("0").toString())
        assertEquals("c", s.getProperty("2").toString())
        assertTrue(s.getProperty("5").isNull())
        assertTrue(s.getProperty("notanindex").isNull())
    }

    @Test
    fun string_equality_caseInsensitive() {
        assertEquals(WslString("Hello"), WslString("hello"))
    }

    @Test
    fun string_equality_coercesToNumber() {
        assertTrue(WslString("5") == WslNumber(5.0))
    }

    @Test
    fun string_equality_coercesToBoolean() {
        assertTrue(WslString("true") == WslBoolean(true))
    }

    @Test
    fun string_equality_nonValueIsFalse() {
        assertFalse(WslString("5").equals(5))
    }

    // --- WslNumber ---

    @Test
    fun number_basics() {
        val n = WslNumber(7.0)
        assertTrue(n.isNumeric())
        assertFalse(n.isBoolean())
        assertFalse(n.isMap())
        assertEquals("7", n.toString())
        assertEquals(7.0, n.toNumber())
    }

    @Test
    fun number_toBoolean_throws() {
        assertFailsWith<WslRuntimeException> { WslNumber(1.0).toBoolean() }
    }

    @Test
    fun number_equality() {
        assertEquals(WslNumber(5.0), WslNumber(5.0))
        assertTrue(WslNumber(5.0) == WslString("5"))
    }

    // --- WslNull ---

    @Test
    fun null_basics() {
        assertTrue(WslNull.isNull())
        assertFalse(WslNull.isNumeric())
        assertFalse(WslNull.isBoolean())
        assertFalse(WslNull.isMap())
        assertFalse(WslNull.toBoolean())
        assertEquals("", WslNull.toString())
    }

    @Test
    fun null_toNumber_throws() {
        assertFailsWith<WslRuntimeException> { WslNull.toNumber() }
    }

    @Test
    fun null_equality() {
        assertEquals(WslNull, WslNull)
        assertFalse(WslNull == WslString(""))
    }

    @Test
    fun null_getProperty_isNull() {
        assertTrue(WslNull.getProperty("anything").isNull())
    }

    // --- WslMap ---

    @Test
    fun map_getAndSet() {
        val map = WslMap(mapOf("a" to WslString("1")))
        assertTrue(map.isMap())
        assertEquals("1", map.getProperty("a").toString())
        assertTrue(map.getProperty("missing").isNull())

        map.setProperty("b", WslString("2"))
        assertEquals("2", map.getProperty("b").toString())
    }

    @Test
    fun map_notNumericNotBoolean() {
        val map = WslMap(emptyMap())
        assertFalse(map.isNumeric())
        assertFalse(map.isBoolean())
        assertFalse(map.toBoolean())
        assertEquals(0.0, map.toNumber())
    }

    // --- compareWith ---

    @Test
    fun compareWith_numeric() {
        val five = WslNumber(5.0)
        val three = WslNumber(3.0)
        assertTrue(five.compareWith(WslComparisonOperator.GT, three))
        assertFalse(five.compareWith(WslComparisonOperator.LT, three))
        assertTrue(five.compareWith(WslComparisonOperator.GTE, WslNumber(5.0)))
        assertTrue(five.compareWith(WslComparisonOperator.LTE, WslNumber(5.0)))
    }

    @Test
    fun compareWith_string() {
        val apple = WslString("apple")
        val banana = WslString("banana")
        assertTrue(apple.compareWith(WslComparisonOperator.LT, banana))
        assertFalse(apple.compareWith(WslComparisonOperator.GT, banana))
    }

    @Test
    fun compareWith_numericStringsCompareNumerically() {
        // "10" > "9" numerically, even though lexically "10" < "9"
        assertTrue(WslString("10").compareWith(WslComparisonOperator.GT, WslString("9")))
    }
}
