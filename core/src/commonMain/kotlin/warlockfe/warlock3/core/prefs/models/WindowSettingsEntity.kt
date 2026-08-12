package warlockfe.warlock3.core.prefs.models

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import warlockfe.warlock3.core.text.WarlockColor

/**
 * Whether a window is in the character's layout. That is all the database actively remembers
 * about a window: where it docks is the docking layout JSON's business (see DOCK_LAYOUT_KEY, with
 * the game-announced location as the seed for windows the JSON does not know), and styling lives
 * in the character's TOML config. The styling columns below are written by nothing and read only
 * by the first-run config migration, kept for the same reason the legacy highlight/name tables
 * are.
 */
@Entity(
    tableName = "WindowSettings",
    primaryKeys = ["characterId", "name"],
)
data class WindowSettingsEntity(
    val characterId: String,
    val name: String,
    @ColumnInfo(defaultValue = "0")
    val open: Boolean = false,
    @ColumnInfo(defaultValue = "-1")
    val textColor: WarlockColor = WarlockColor.Unspecified,
    @ColumnInfo(defaultValue = "-1")
    val backgroundColor: WarlockColor = WarlockColor.Unspecified,
    val fontFamily: String? = null,
    val fontSize: Float? = null,
    val fontWeight: Int? = null,
    @ColumnInfo(defaultValue = "0")
    val nameFilter: Boolean = false,
)
