package warlockfe.warlock3.core.client

sealed class WarlockAction {
    data class SendCommand(val command: String) : WarlockAction()

    data class OpenMenu(val onClick: () -> Int) : WarlockAction()

    data class OpenLink(val url: String) : WarlockAction()
}
