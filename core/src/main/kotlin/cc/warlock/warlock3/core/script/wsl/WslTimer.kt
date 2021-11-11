package cc.warlock.warlock3.core.script.wsl

import java.math.BigDecimal

class WslTimer : WslValue {
    private val startTime = System.currentTimeMillis()
    override fun toBoolean(): Boolean {
        throw WslRuntimeException("Cannot convert timer to boolean")
    }

    override fun toNumber(): BigDecimal {
        return ((System.currentTimeMillis() - startTime) / 1000L).toBigDecimal()
    }

    override fun isNumeric(): Boolean {
        return true
    }

    override fun toString(): String {
        return toNumber().toString()
    }

    override fun getProperty(key: String): WslValue {
        return WslNull
    }

    override fun setProperty(key: String, value: WslValue) {}

    override fun isMap(): Boolean {
        return false
    }
}