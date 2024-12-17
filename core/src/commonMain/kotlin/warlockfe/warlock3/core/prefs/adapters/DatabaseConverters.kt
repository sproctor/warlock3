package warlockfe.warlock3.core.prefs.adapters

import androidx.room.TypeConverter
import warlockfe.warlock3.core.text.WarlockColor
import warlockfe.warlock3.core.window.WindowLocation
import java.nio.ByteBuffer
import java.util.*

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
    fun toUUID(databaseValue: ByteArray): UUID {
        val byteBuffer = ByteBuffer.wrap(databaseValue)
        val high = byteBuffer.long
        val low = byteBuffer.long
        return UUID(high, low)
    }

    @TypeConverter
    fun fromUUID(value: UUID): ByteArray {
        return value.toByteArray()
    }

    private fun UUID.toByteArray(): ByteArray {
        val buffer = ByteBuffer.wrap(ByteArray(16))
        buffer.putLong(mostSignificantBits)
        buffer.putLong(leastSignificantBits)
        return buffer.array()
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