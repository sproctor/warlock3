package warlockfe.warlock3.scripting.wsl

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import warlockfe.warlock3.core.client.WarlockClient

class WslComponents(
    private val client: WarlockClient,
) : WslValue {
    override fun toString(): String = client.getComponents().toString()

    override fun toBoolean(): Boolean = false

    override fun toNumber(): BigDecimal = BigDecimal.ZERO

    override fun isNumeric(): Boolean = false

    override fun isBoolean(): Boolean = false

    override fun getProperty(key: String): WslValue = client.getComponent(key)?.let { WslString(it.toString()) } ?: WslNull

    override fun setProperty(
        key: String,
        value: WslValue,
    ): Unit = throw WslRuntimeException("Cannot set properties of components")

    override fun isMap(): Boolean = true
}
