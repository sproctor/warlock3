package warlockfe.warlock3.core.util

/**
 * A small, dependency-free SHA-256 implementation. We only need it to compute the `keyHash`
 * (sha256 of the EAccess game key) that the MUD Mobile API expects, and a multiplatform crypto
 * library would be overkill for one hash. The output matches the standard hex digest, e.g.
 * `sha256Hex("")` == "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855".
 */
fun sha256Hex(input: String): String = sha256Hex(input.encodeToByteArray())

fun sha256Hex(bytes: ByteArray): String {
    val digest = sha256(bytes)
    val sb = StringBuilder(digest.size * 2)
    for (b in digest) {
        val v = b.toInt() and 0xff
        sb.append(HEX_CHARS[v ushr 4])
        sb.append(HEX_CHARS[v and 0x0f])
    }
    return sb.toString()
}

private const val HEX_CHARS = "0123456789abcdef"

private val K =
    intArrayOf(
        0x428a2f98,
        0x71374491,
        -0x4a3f0431,
        -0x164a245b,
        0x3956c25b,
        0x59f111f1,
        -0x6dc07d5c,
        -0x54e3a12b,
        -0x27f85568,
        0x12835b01,
        0x243185be,
        0x550c7dc3,
        0x72be5d74,
        -0x7f214e02,
        -0x6423f959,
        -0x3e640e8c,
        -0x1b64963f,
        -0x1041b87a,
        0x0fc19dc6,
        0x240ca1cc,
        0x2de92c6f,
        0x4a7484aa,
        0x5cb0a9dc,
        0x76f988da,
        -0x67c1aeae,
        -0x57ce3993,
        -0x4ffcd838,
        -0x40a68039,
        -0x391ff40d,
        -0x2a586eb9,
        0x06ca6351,
        0x14292967,
        0x27b70a85,
        0x2e1b2138,
        0x4d2c6dfc,
        0x53380d13,
        0x650a7354,
        0x766a0abb,
        -0x7e3d36d2,
        -0x6d8dd37b,
        -0x5d40175f,
        -0x57e599b5,
        -0x3db47490,
        -0x3893ae5d,
        -0x2e6d17e7,
        -0x2966f9dc,
        -0xbf1ca7b,
        0x106aa070,
        0x19a4c116,
        0x1e376c08,
        0x2748774c,
        0x34b0bcb5,
        0x391c0cb3,
        0x4ed8aa4a,
        0x5b9cca4f,
        0x682e6ff3,
        0x748f82ee,
        0x78a5636f,
        -0x7b3787ec,
        -0x7338fdf8,
        -0x6f410006,
        -0x5baf9315,
        -0x41065c09,
        -0x398e870e,
    )

private fun sha256(message: ByteArray): ByteArray {
    var h0 = 0x6a09e667
    var h1 = -0x4498517b
    var h2 = 0x3c6ef372
    var h3 = -0x5ab00ac6
    var h4 = 0x510e527f
    var h5 = -0x64fa9774
    var h6 = 0x1f83d9ab
    var h7 = 0x5be0cd19

    // Pre-processing (padding) to a multiple of 64 bytes.
    val originalBitLength = message.size.toLong() * 8
    val paddingLength = ((56 - (message.size + 1) % 64) + 64) % 64
    val padded = ByteArray(message.size + 1 + paddingLength + 8)
    message.copyInto(padded)
    padded[message.size] = 0x80.toByte()
    for (i in 0 until 8) {
        padded[padded.size - 1 - i] = (originalBitLength ushr (8 * i)).toByte()
    }

    val w = IntArray(64)
    var chunk = 0
    while (chunk < padded.size) {
        for (i in 0 until 16) {
            val j = chunk + i * 4
            w[i] =
                ((padded[j].toInt() and 0xff) shl 24) or
                ((padded[j + 1].toInt() and 0xff) shl 16) or
                ((padded[j + 2].toInt() and 0xff) shl 8) or
                (padded[j + 3].toInt() and 0xff)
        }
        for (i in 16 until 64) {
            val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
            val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
        }

        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4
        var f = h5
        var g = h6
        var h = h7

        for (i in 0 until 64) {
            val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val ch = (e and f) xor (e.inv() and g)
            val temp1 = h + s1 + ch + K[i] + w[i]
            val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val temp2 = s0 + maj

            h = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }

        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
        h5 += f
        h6 += g
        h7 += h

        chunk += 64
    }

    val result = ByteArray(32)
    intArrayOf(h0, h1, h2, h3, h4, h5, h6, h7).forEachIndexed { index, value ->
        result[index * 4] = (value ushr 24).toByte()
        result[index * 4 + 1] = (value ushr 16).toByte()
        result[index * 4 + 2] = (value ushr 8).toByte()
        result[index * 4 + 3] = value.toByte()
    }
    return result
}
