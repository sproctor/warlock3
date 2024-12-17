package warlockfe.warlock3.core.prefs.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.window.WindowLocation

@Entity(
    tableName = "WindowSettings",
    primaryKeys = ["characterId", "name"],
)
data class WindowSettingsEntity(
    val characterId: String,
    val name: String,
    val width: Int,
    val height: Int,
    val location: WindowLocation,
    val position: Int,
    @ColumnInfo(defaultValue = "-1")
    val textColor: WarlockColor,
    @ColumnInfo(defaultValue = "-1")
    val backgroundColor: WarlockColor,
    val fontFamily: String?,
    val fontSize: Float?,
)
