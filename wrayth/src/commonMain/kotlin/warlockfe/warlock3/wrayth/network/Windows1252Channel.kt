package warlockfe.warlock3.wrayth.network

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.TooLongLineException
import io.ktor.utils.io.discard
import io.ktor.utils.io.exhausted
import io.ktor.utils.io.peek
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readByte
import kotlinx.io.Buffer
import kotlinx.io.UnsafeIoApi
import kotlinx.io.readByteArray
import kotlinx.io.unsafe.UnsafeBufferOperations
import kotlinx.io.unsafe.withData
import warlockfe.warlock3.core.util.decodeWindows1252
import kotlin.math.min

// The game speaks windows-1252 over whichever transport it arrives on: a TCP socket for
// [NetworkSocket], WebSocket frames for [WarlockWebSocket]. Both end up as a channel of exactly the
// same bytes, so the decoding lives here rather than once per transport.

/**
 * Reads up to the next CR, LF, or CRLF, which is consumed but not returned. Returns null once the
 * peer is gone and nothing is left; a final unterminated line is returned before that.
 */
internal suspend fun ByteReadChannel.readWindows1252Line(max: Int = Int.MAX_VALUE): String? {
    val result = StringBuilder()
    val completed = readWindows1252LineTo(result, max)
    return if (!completed) null else result.toString()
}

private suspend fun ByteReadChannel.readWindows1252LineTo(
    out: Appendable,
    max: Int,
): Boolean {
    if (exhausted()) return false
    if (isClosedForRead) return false

    Buffer().use { lineBuffer ->
        while (!isClosedForRead) {
            while (!exhausted()) {
                when (val b = readByte()) {
                    CR -> {
                        // CR is only half a terminator until the next byte says otherwise, so wait
                        // for one. There may be none - the peer can hang up on the CR - and the line
                        // it ended is still a line either way.
                        awaitContent()
                        if (peek(1)?.get(0) == LF) {
                            discard(1)
                        }
                        out.append(lineBuffer.readWindows1252String())
                        return true
                    }

                    LF -> {
                        out.append(lineBuffer.readWindows1252String())
                        return true
                    }

                    else -> {
                        lineBuffer.writeByte(b)
                    }
                }
            }
            if (lineBuffer.size >= max) {
                throw TooLongLineException("Line exceeds limit of $max characters")
            }

            awaitContent()
        }

        return (lineBuffer.size > 0).also { remaining ->
            if (remaining) {
                out.append(lineBuffer.readWindows1252String())
            }
        }
    }
}

/**
 * Reads whatever has arrived, waiting for at least [min] bytes, into [buffer]. Null once the peer is
 * gone and there is nothing left to read, as with [readWindows1252Line].
 */
internal suspend fun ByteReadChannel.readWindows1252Available(
    buffer: ByteArray,
    min: Int,
): String? {
    awaitContent(min)
    val len = readAvailable(buffer)
    if (len < 0) return null
    return buffer.decodeWindows1252(0, len)
}

private const val CR: Byte = '\r'.code.toByte()
private const val LF: Byte = '\n'.code.toByte()

private fun Buffer.readWindows1252String(): String = readWindows1252(size)

@OptIn(UnsafeIoApi::class)
private fun Buffer.readWindows1252(byteCount: Long): String {
    // Invariant: byteCount was request()'ed into this buffer beforehand
    if (byteCount == 0L) return ""

    UnsafeBufferOperations.forEachSegment(this) { ctx, segment ->
        if (segment.size >= byteCount) {
            var result: String
            ctx.withData(segment) { data, pos, limit ->
                result = data.decodeWindows1252(pos, min(limit, pos + byteCount.toInt()))
                skip(byteCount)
                return result
            }
        }
        // If the string spans multiple segments, delegate to readBytes()
        return readByteArray(byteCount.toInt()).decodeWindows1252()
    }
    error("Unreacheable")
}
