package warlockfe.warlock3.compose.ui.settings

import org.jetbrains.compose.resources.DrawableResource
import warlockfe.warlock3.compose.generated.resources.Res
import warlockfe.warlock3.compose.generated.resources.arrow_right_alt
import warlockfe.warlock3.compose.generated.resources.circle_filled
import warlockfe.warlock3.compose.generated.resources.edit
import warlockfe.warlock3.compose.generated.resources.front_hand
import warlockfe.warlock3.compose.generated.resources.group
import warlockfe.warlock3.compose.generated.resources.login
import warlockfe.warlock3.compose.generated.resources.palette
import warlockfe.warlock3.compose.generated.resources.settings_filled
import warlockfe.warlock3.compose.generated.resources.space_dashboard
import warlockfe.warlock3.compose.generated.resources.space_dashboard_filled
import warlockfe.warlock3.compose.generated.resources.star_shine
import warlockfe.warlock3.compose.generated.resources.visibility_filled

// Sections the settings pages are grouped under in the nav. General has a blank title so it renders
// on its own with no section header.
enum class SettingsGroup(
    val title: String,
) {
    General(""),
    Appearance("Appearance"),
    Game("Game"),
    Account("Account"),
}

enum class SettingsPage(
    val title: String,
    val group: SettingsGroup,
    val icon: DrawableResource,
) {
    General("General", SettingsGroup.General, Res.drawable.settings_filled),
    Presets("Presets", SettingsGroup.Appearance, Res.drawable.palette),
    Windows("Windows", SettingsGroup.Appearance, Res.drawable.space_dashboard_filled),
    Highlights("Highlights", SettingsGroup.Appearance, Res.drawable.star_shine),
    Names("Names", SettingsGroup.Appearance, Res.drawable.visibility_filled),
    Alterations("Alterations", SettingsGroup.Appearance, Res.drawable.edit),
    Actions("Actions", SettingsGroup.Game, Res.drawable.front_hand),
    Macros("Macros", SettingsGroup.Game, Res.drawable.space_dashboard),
    Aliases("Aliases", SettingsGroup.Game, Res.drawable.arrow_right_alt),
    Variables("Variables", SettingsGroup.Game, Res.drawable.circle_filled),
    Accounts("Accounts", SettingsGroup.Account, Res.drawable.login),
    Characters("Characters", SettingsGroup.Account, Res.drawable.group),
}
