package warlockfe.warlock3.core.sge

interface SgeClientFactory {
    fun create(): SgeClient
}
