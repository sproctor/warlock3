package warlockfe.warlock3.core.prefs.adapters

import androidx.room.TypeConverter
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.window.WindowLocation
import kotlin.uuid.Uuid

class DatabaseConverters {
    @TypeConverter
    fun toWindowLocation(databaseValue: String): WindowLocation = WindowLocation.entries.first { it.value == databaseValue }

    @TypeConverter
    fun fromWindowLocation(value: WindowLocation): String = value.value

    @TypeConverter
    fun toUuid(databaseValue: ByteArray): Uuid = Uuid.fromByteArray(databaseValue)

    @TypeConverter
    fun fromUUID(value: Uuid): ByteArray = value.toByteArray()

    @TypeConverter
    fun toWarlockColor(databaseValue: Long): WarlockColor = WarlockColor(databaseValue)

    @TypeConverter
    fun fromWarlockColor(value: WarlockColor): Long = value.argb
}
