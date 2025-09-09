package warlockfe.warlock3.scripting.util

import com.ionspin.kotlin.bignum.decimal.BigDecimal

fun String.toBigDecimalOrNull(): BigDecimal? {
    return try {
        BigDecimal.parseString(this)
    } catch (_: Exception /* ArithmeticException */) { // I think all exceptions here are ArithmeticException, but catching everything in case
        null
    }
}
