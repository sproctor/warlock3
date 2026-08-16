package warlockfe.warlock3.core.client

sealed class WarlockAction {
    data class SendCommand(
        val command: String,
    ) : WarlockAction()

    data class SendCommandWithLookup(
        val command: suspend () -> String,
    ) : WarlockAction()

    data class OpenMenu(
        val onClick: () -> Int,
    ) : WarlockAction()

    // A panel widget's command: [command] is sent to the server verbatim (it is often an internal
    // form like "_ready weapon"), while [echo] is what the user sees echoed, when present.
    data class SendWidgetCommand(
        val command: String,
        val echo: String?,
    ) : WarlockAction()

    // A panel widget asking for the server-driven context menu for [exist] (menuLink/menuImage).
    data class RequestMenu(
        val exist: String,
        val noun: String?,
    ) : WarlockAction()

    data class OpenLink(
        val url: String,
    ) : WarlockAction()

    // A closeButton dismissing the panel it sits in, named by [id] (the panel's id, which is also
    // the window's name). It carries its own [command] and [echo] rather than raising a separate
    // SendWidgetCommand, because the real client sends the command and closes after, and the two
    // are not independent: a resident panel runs the command but is not closed.
    data class ClosePanel(
        val id: String,
        val command: String?,
        val echo: String?,
    ) : WarlockAction()
}
