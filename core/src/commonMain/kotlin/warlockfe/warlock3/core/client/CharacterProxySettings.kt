package warlockfe.warlock3.core.client

data class CharacterProxySettings(
    val enabled: Boolean,
    val launchCommand: String?,
    val host: String?,
    val port: String?,
    val delay: Long?,
)