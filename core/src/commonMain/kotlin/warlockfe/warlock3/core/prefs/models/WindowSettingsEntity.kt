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
    // Where the window lives when [open], and its remembered placement when closed - closing a
    // window keeps these so it reopens where the user left it.
    val location: WindowLocation?,
    val position: Int?,
    // Whether the window is in the layout. Only open windows take part in a dock's 0..n
    // numbering; a closed window's position is frozen at its close-time index.
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
