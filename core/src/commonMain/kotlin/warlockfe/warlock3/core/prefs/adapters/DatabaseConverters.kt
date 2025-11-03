package warlockfe.warlock3.core.prefs.adapters

import androidx.room.TypeConverter
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.window.WindowLocation
import kotlin.uuid.Uuid

class DatabaseConverters {
    @TypeConverter
    fun toWindowLocation(databaseValue: String): WindowLocation {
        return WindowLocation.entries.first { it.value == databaseValue }
    }

    @TypeConverter
    fun fromWindowLocation(value: WindowLocation): String {
        return value.value
    }

    @TypeConverter
    fun toUuid(databaseValue: ByteArray): Uuid {
        return Uuid.fromByteArray(databaseValue)
    }

    @TypeConverter
    fun fromUUID(value: Uuid): ByteArray {
        return value.toByteArray()
    }

    @TypeConverter
    fun toWarlockColor(databaseValue: Long): WarlockColor {
        return WarlockColor(databaseValue)
    }

    @TypeConverter
    fun fromWarlockColor(value: WarlockColor): Long {
        return value.argb
    }
}