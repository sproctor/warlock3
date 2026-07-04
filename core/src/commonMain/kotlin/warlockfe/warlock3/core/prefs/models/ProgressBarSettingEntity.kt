package warlockfe.warlock3.core.prefs.models

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import warlockfe.warlock3.core.text.WarlockColor

@Entity(
    tableName = "ProgressBarSetting",
    primaryKeys = ["characterId", "id"],
)
data class ProgressBarSettingEntity(
    val characterId: String,
    val id: String,
    @ColumnInfo(defaultValue = "-1")
    val barColor: WarlockColor,
    @ColumnInfo(defaultValue = "-1")
    val backgroundColor: WarlockColor,
    @ColumnInfo(defaultValue = "-1")
    val textColor: WarlockColor,
)
