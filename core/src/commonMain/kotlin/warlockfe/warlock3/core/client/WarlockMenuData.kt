package warlockfe.warlock3.core.client

data class WarlockMenuData(
    val id: Int,
    val items: List<WarlockMenuItem>,
)

data class WarlockMenuItem(
    val label: String,
    val category: String,
    val action: () -> Unit,
)
