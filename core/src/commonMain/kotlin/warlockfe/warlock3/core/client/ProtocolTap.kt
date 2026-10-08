package warlockfe.warlock3.core.client

/** See [WarlockClient.tapProtocol]: the lines before the tap was made, and how to remove it. */
class ProtocolTap(
    val history: List<String>,
    val remove: () -> Unit,
)
