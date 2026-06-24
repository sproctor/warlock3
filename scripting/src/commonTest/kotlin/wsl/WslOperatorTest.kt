package warlockfe.warlock3.scripting.wsl

import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WslOperatorTest {
    private fun num(value: Int) = WslNumber(value.toBigDecimal())

    // --- WslAdditiveOperator ---

    @Test
    fun add_numbers() {
        val result = WslAdditiveOperator.ADD.getValue(num(2), num(3))
        assertTrue(result.isNumeric())
        assertEquals("5", result.toString())
    }

    @Test
    fun add_stringsConcatenates() {
        val result = WslAdditiveOperator.ADD.getValue(WslString("foo"), WslString("bar"))
        assertEquals("foobar", result.toString())
    }

    @Test
    fun add_numberAndStringConcatenates() {
        // Non-numeric string forces string concatenation
        val result = WslAdditiveOperator.ADD.getValue(num(2), WslString("abc"))
        assertEquals("2abc", result.toString())
    }

    @Test
    fun subtract_numbers() {
        val result = WslAdditiveOperator.SUB.getValue(num(10), num(4))
        assertEquals("6", result.toString())
    }

    @Test
    fun subtract_nonNumericThrows() {
        assertFailsWith<WslRuntimeException> {
            WslAdditiveOperator.SUB.getValue(WslString("abc"), num(1))
        }
    }

    // --- WslMultiplicativeOperator ---

    @Test
    fun multiply_numbers() {
        val result = WslMultiplicativeOperator.MULT.getValue(num(6), num(7))
        assertEquals("42", result.toString())
    }

    @Test
    fun multiply_stringRepeats() {
        val result = WslMultiplicativeOperator.MULT.getValue(WslString("ab"), num(3))
        assertEquals("ababab", result.toString())
    }

    @Test
    fun multiply_nonNumericSecondArgThrows() {
        assertFailsWith<WslRuntimeException> {
            WslMultiplicativeOperator.MULT.getValue(num(2), WslString("abc"))
        }
    }

    @Test
    fun divide_numbers() {
        val result = WslMultiplicativeOperator.DIV.getValue(num(10), num(4))
        assertEquals("2.5", result.toString())
    }

    @Test
    fun divide_byZeroThrows() {
        assertFailsWith<WslRuntimeException> {
            WslMultiplicativeOperator.DIV.getValue(num(1), num(0))
        }
    }

    // --- WslInfixOperator ---

    @Test
    fun contains_substring() {
        val yes = WslInfixOperator.CONTAINS.getValue(WslString("hello world"), WslString("world"))
        assertTrue(yes.toBoolean())
        val no = WslInfixOperator.CONTAINS.getValue(WslString("hello world"), WslString("xyz"))
        assertFalse(no.toBoolean())
    }

    @Test
    fun contains_mapChecksKeyPresence() {
        val map = WslMap(mapOf("key" to WslString("value")))
        assertTrue(WslInfixOperator.CONTAINS.getValue(map, WslString("key")).toBoolean())
        assertFalse(WslInfixOperator.CONTAINS.getValue(map, WslString("absent")).toBoolean())
    }

    @Test
    fun containsRegex_matches() {
        val yes = WslInfixOperator.CONTAINSRE.getValue(WslString("order 66"), WslString("[0-9]+"))
        assertTrue(yes.toBoolean())
        val no = WslInfixOperator.CONTAINSRE.getValue(WslString("no digits"), WslString("[0-9]+"))
        assertFalse(no.toBoolean())
    }
}
