package warlockfe.warlock3.scripting.wsl

import warlockfe.warlock3.core.client.WarlockClient

class WslComponents(
    private val client: WarlockClient,
) : WslValue {
    override fun toText(): String = client.getComponents().mapValues { it.value.toText() }.toString()

    override fun toString(): String = client.getComponents().mapValues { it.value.toText() }.toString()

    override fun toBoolean(): Boolean = false

    override fun toNumber(): Double = 0.0

    override fun isNumeric(): Boolean = false

    override fun isBoolean(): Boolean = false

    override fun getProperty(key: String): WslValue = client.getComponent(key)?.let { WslString(it.toText()) } ?: WslNull

    override fun setProperty(
        key: String,
        value: WslValue,
    ): Unit = throw WslRuntimeException("Cannot set properties of components")

    override fun isMap(): Boolean = true
}
