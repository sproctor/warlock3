package warlockfe.warlock3.core.prefs.models

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.window.WindowLocation

@Entity(
    tableName = "WindowSettings",
    primaryKeys = ["characterId", "name"],
)
data class WindowSettingsEntity(
    val characterId: String,
    val name: String,
    val width: Int?,
    val height: Int?,
    // The window's dock, and its slot in that dock's total order - kept across close/reopen so
    // the window comes back where the user left it. Slots may be sparse; only order matters.
    val location: WindowLocation?,
    val position: Int?,
    // Whether the window is in the layout. A closed window keeps its location and slot as the
    // remembered placement.
    @ColumnInfo(defaultValue = "0")
    val open: Boolean = false,
    @ColumnInfo(defaultValue = "-1")
    val textColor: WarlockColor,
    @ColumnInfo(defaultValue = "-1")
    val backgroundColor: WarlockColor,
    val fontFamily: String?,
    val fontSize: Float?,
    val fontWeight: Int?,
    @ColumnInfo(defaultValue = "0")
    val nameFilter: Boolean = false,
)
