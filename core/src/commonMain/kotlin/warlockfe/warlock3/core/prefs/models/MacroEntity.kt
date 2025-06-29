package warlockfe.warlock3.core.prefs.models

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "macro",
    primaryKeys = ["characterId", "key", "keyCode", "ctrl", "alt", "shift", "meta"],
)
data class MacroEntity(
    val characterId: String,
    @ColumnInfo(defaultValue = "")
    val key: String,
    val value: String,
    @ColumnInfo(defaultValue = "0")
    val keyCode: Long,
    @ColumnInfo(defaultValue = "0")
    val ctrl: Boolean,
    @ColumnInfo(defaultValue = "0")
    val alt: Boolean,
    @ColumnInfo(defaultValue = "0")
    val shift: Boolean,
    @ColumnInfo(defaultValue = "0")
    val meta: Boolean,
)
