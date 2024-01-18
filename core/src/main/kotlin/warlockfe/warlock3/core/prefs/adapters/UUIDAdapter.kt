package warlockfe.warlock3.core.prefs.adapters

import app.cash.sqldelight.ColumnAdapter
import java.nio.ByteBuffer
import java.util.*

object UUIDAdapter : ColumnAdapter<UUID, ByteArray> {
    override fun decode(databaseValue: ByteArray): UUID {
        val byteBuffer = ByteBuffer.wrap(databaseValue)
        val high = byteBuffer.long
        val low = byteBuffer.long
        return UUID(high, low)
    }

    override fun encode(value: UUID): ByteArray {
        return value.toByteArray()
    }
}

fun UUID.toByteArray(): ByteArray {
    val buffer = ByteBuffer.wrap(ByteArray(16))
    buffer.putLong(mostSignificantBits)
    buffer.putLong(leastSignificantBits)
    return buffer.array()
}