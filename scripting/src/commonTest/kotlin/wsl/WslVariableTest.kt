package warlockfe.warlock3.scripting.wsl

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WslVariableTest {
    @Test
    fun wrapsIntAsNumeric() {
        val v = WslVariable { 42 }
        assertTrue(v.isNumeric())
        assertFalse(v.isBoolean())
        assertEquals("42", v.toNumber().toPlainString())
    }

    @Test
    fun wrapsDouble() {
        val v = WslVariable { 3.5 }
        assertTrue(v.isNumeric())
        assertEquals("3.5", v.toNumber().toPlainString())
    }

    @Test
    fun wrapsBoolean() {
        val v = WslVariable { true }
        assertTrue(v.isBoolean())
        assertFalse(v.isNumeric())
        assertTrue(v.toBoolean())
    }

    @Test
    fun stringBooleanCoercion() {
        val v = WslVariable { "true" }
        assertFalse(v.isBoolean()) // underlying is a String, not Boolean
        assertTrue(v.toBoolean())
    }

    @Test
    fun nonNumericStringNumberDefaultsToZero() {
        val v = WslVariable { "hello" }
        assertEquals("0", v.toNumber().toPlainString())
    }

    @Test
    fun nullValue() {
        val v = WslVariable { null }
        assertTrue(v.isNull())
        assertFalse(v.isNumeric())
        assertFalse(v.isBoolean())
        assertFalse(v.isMap())
    }

    @Test
    fun mapProperty() =
        runTest {
            val v = WslVariable { mapOf("count" to 5) }
            assertTrue(v.isMap())
            // getProperty wraps the value in another WslVariable, so inspect it numerically
            assertEquals("5", v.getProperty("count").toNumber().toPlainString())
            assertTrue(v.getProperty("missing").isNull())
        }

    @Test
    fun nonMapPropertyIsNull() =
        runTest {
            val v = WslVariable { 42 }
            assertTrue(v.getProperty("anything").isNull())
        }

    @Test
    fun getterIsEvaluatedLazily() {
        var backing = 1
        val v = WslVariable { backing }
        assertEquals("1", v.toNumber().toPlainString())
        backing = 99
        assertEquals("99", v.toNumber().toPlainString())
    }

    @Test
    fun mutableMapSetProperty() {
        val backing = mutableMapOf<String, String>()
        val v = WslVariable { backing }
        v.setProperty("key", WslString("value"))
        assertEquals("value", backing["key"])
    }
}
