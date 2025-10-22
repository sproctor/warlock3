package warlockfe.warlock3.core.sge

data class SgeSettings(
    val host: String,
    val port: Int,
    val certificate: ByteArray,
    val secure: Boolean,
)
