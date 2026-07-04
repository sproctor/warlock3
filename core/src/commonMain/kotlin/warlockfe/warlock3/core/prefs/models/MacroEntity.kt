package warlockfe.warlock3.core.prefs.models

import androidx.room3.ColumnInfo
import androidx.room3.Entity

@Entity(
    tableName = "macro",
    primaryKeys = ["characterId", "key", "keyCode", "ctrl", "alt", "shift", "meta"],
)
data class MacroEntity(
    val characterId: String,
    @ColumnInfo(defaultValue = "")
    val key: String,
    val value: String,
    // The keyCode/modifier columns below are deprecated: `key` now encodes the full key combo and is
    // the authoritative identifier. They are retained only because they're part of the legacy
    // composite primary key.
    @Deprecated("Redundant with `key`; retained only for the legacy composite primary key.")
    @ColumnInfo(defaultValue = "0")
    val keyCode: Long,
    @Deprecated("Redundant with `key`; retained only for the legacy composite primary key.")
    @ColumnInfo(defaultValue = "0")
    val ctrl: Boolean,
    @Deprecated("Redundant with `key`; retained only for the legacy composite primary key.")
    @ColumnInfo(defaultValue = "0")
    val alt: Boolean,
    @Deprecated("Redundant with `key`; retained only for the legacy composite primary key.")
    @ColumnInfo(defaultValue = "0")
    val shift: Boolean,
    @Deprecated("Redundant with `key`; retained only for the legacy composite primary key.")
    @ColumnInfo(defaultValue = "0")
    val meta: Boolean,
)
