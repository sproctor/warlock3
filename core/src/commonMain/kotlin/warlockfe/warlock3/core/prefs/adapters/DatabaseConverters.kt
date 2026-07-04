package warlockfe.warlock3.core.prefs.adapters

import androidx.room3.ColumnTypeConverter
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.window.WindowLocation
import kotlin.uuid.Uuid

class DatabaseConverters {
    @ColumnTypeConverter
    fun toWindowLocation(databaseValue: String): WindowLocation = WindowLocation.entries.first { it.value == databaseValue }

    @ColumnTypeConverter
    fun fromWindowLocation(value: WindowLocation): String = value.value

    @ColumnTypeConverter
    fun toUuid(databaseValue: ByteArray): Uuid =
        // New rows store the UUID as 16 raw bytes. Legacy rows (written by old app versions and
        // carried forward unconverted by MIGRATION_14_16's INSERT..SELECT) hold the 36-char string
        // form instead, which SQLite's dynamic typing returns as its UTF-8 bytes. Parse whichever we
        // get so reading these now-retired tables for the one-time DB->TOML migration can't crash.
        if (databaseValue.size == 16) {
            Uuid.fromByteArray(databaseValue)
        } else {
            Uuid.parse(databaseValue.decodeToString())
        }

    @ColumnTypeConverter
    fun fromUUID(value: Uuid): ByteArray = value.toByteArray()

    @ColumnTypeConverter
    fun toWarlockColor(databaseValue: Long): WarlockColor = WarlockColor(databaseValue)

    @ColumnTypeConverter
    fun fromWarlockColor(value: WarlockColor): Long = value.argb
}
