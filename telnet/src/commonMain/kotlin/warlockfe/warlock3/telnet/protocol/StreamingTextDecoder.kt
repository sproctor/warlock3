package warlockfe.warlock3.telnet.protocol

/**
 * Turns the bytes a MUD sends into text, a chunk at a time.
 *
 * MUDs are UTF-8 by now, mostly, but the older ones send Latin-1 (or its Windows cousin) and say
 * nothing about it, and a few send both, in different places. So every valid UTF-8 sequence is
 * taken as UTF-8, and every byte that is not part of one is taken as Latin-1. That reads both
 * kinds of server correctly, and a Latin-1 accent that happens to look like the start of a UTF-8
 * sequence but is not followed by one still comes out as the accent.
 *
 * A multi-byte sequence can be cut by the end of a socket read, so an incomplete one is held for
 * the next chunk rather than judged on the spot.
 */
class StreamingTextDecoder {
    // An incomplete UTF-8 sequence carried over from the last chunk: the bytes so far, and how many
    // the lead byte said there would be.
    private val pending = ByteArray(4)
    private var pendingLength = 0
    private var pendingNeeded = 0

    fun decode(
        bytes: ByteArray,
        length: Int = bytes.size,
    ): String {
        val out = StringBuilder(length)
        for (i in 0 until length) {
            val b = bytes[i]
            val value = b.toInt() and 0xFF
            if (pendingLength > 0) {
                if (value in 0x80..0xBF) {
                    pending[pendingLength++] = b
                    if (pendingLength == pendingNeeded) {
                        appendPending(out)
                    }
                    continue
                }
                // Not a continuation byte, so what was pending was never UTF-8.
                appendPendingAsLatin1(out)
            }
            when {
                value < 0x80 -> out.append(value.toChar())

                value in 0xC2..0xDF -> startSequence(b, 2)

                value in 0xE0..0xEF -> startSequence(b, 3)

                value in 0xF0..0xF4 -> startSequence(b, 4)

                // A lone continuation byte, an overlong lead (C0, C1), or beyond Unicode (F5+).
                else -> out.append(value.toChar())
            }
        }
        return out.toString()
    }

    /** Whatever is still held as a possible UTF-8 sequence, as Latin-1: for the end of the stream. */
    fun flush(): String {
        if (pendingLength == 0) return ""
        val out = StringBuilder()
        appendPendingAsLatin1(out)
        return out.toString()
    }

    private fun startSequence(
        lead: Byte,
        needed: Int,
    ) {
        pending[0] = lead
        pendingLength = 1
        pendingNeeded = needed
    }

    private fun appendPending(out: StringBuilder) {
        val decoded =
            try {
                pending.decodeToString(0, pendingLength, throwOnInvalidSequence = true)
            } catch (_: CharacterCodingException) {
                // Well-formed on the surface (lead byte plus the right number of continuation bytes)
                // but not a valid code point: overlong, a surrogate, or past U+10FFFF.
                null
            }
        if (decoded != null) {
            out.append(decoded)
            pendingLength = 0
        } else {
            appendPendingAsLatin1(out)
        }
    }

    private fun appendPendingAsLatin1(out: StringBuilder) {
        for (i in 0 until pendingLength) {
            out.append((pending[i].toInt() and 0xFF).toChar())
        }
        pendingLength = 0
    }
}
