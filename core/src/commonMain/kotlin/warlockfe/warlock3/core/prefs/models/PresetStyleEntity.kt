package warlockfe.warlock3.core.prefs.models

import androidx.room.Entity
import warlockfe.warlock3.core.text.WarlockColor

@Entity(
    tableName = "presetstyle",
    primaryKeys = ["presetId", "characterId"],
    )
data class PresetStyleEntity(
    val presetId: String,
    val characterId: String,
    val textColor: WarlockColor,
    val backgroundColor: WarlockColor,
    val entireLine: Boolean,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val fontFamily: String?,
    val fontSize: Float?,
)
