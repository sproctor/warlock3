package warlockfe.warlock3.scripting.lua

/**
 * Regular expressions for scripts, Kotlin's flavour, which unlike Lua patterns has alternation,
 * optional groups and counted repeats. Compiled once per pattern and flags and kept, a few hundred
 * at most, since a script tends to use the same handful in a loop.
 *
 * Positions given to and returned to scripts are Lua's: 1-based, with the end inclusive.
 */
internal class LuaRegex {
    private val cache = LinkedHashMap<String, Regex>()

    fun compile(
        pattern: String,
        flags: String?,
    ): Regex {
        val key = (flags ?: "") + "\u0000" + pattern
        cache[key]?.let { return it }
        val options =
            (flags ?: "").map { flag ->
                when (flag) {
                    'i' -> RegexOption.IGNORE_CASE
                    'm' -> RegexOption.MULTILINE
                    else -> throw IllegalArgumentException("unknown regex flag '$flag'; use i (ignore case) or m (multiline)")
                }
            }
        val regex =
            try {
                Regex(pattern, options.toSet())
            } catch (e: Exception) {
                throw IllegalArgumentException("bad regex \"$pattern\": ${e.message}")
            }
        if (cache.size >= MAX_CACHED) cache.remove(cache.keys.first())
        cache[key] = regex
        return regex
    }

    /** What a match captured: its groups, or the whole match when the pattern has none. */
    fun captures(match: MatchResult): List<String?> =
        if (match.groups.size <= 1) {
            listOf(match.value)
        } else {
            (1 until match.groups.size).map { match.groups[it]?.value }
        }

    /**
     * A match in [text] as `{start=, finish=, match=, captures={...}, count=}`, with Lua's
     * positions: byte offsets into the UTF-8 string Lua holds, 1-based, the end inclusive. `count`
     * is how many captures there are, nil ones included.
     */
    fun describe(
        match: MatchResult,
        text: String,
    ): Map<String, Any?> {
        val captures = captures(match)
        return mapOf(
            "start" to byteOffset(text, match.range.first) + 1,
            "finish" to byteOffset(text, match.range.last + 1),
            "match" to match.value,
            "captures" to captures,
            "count" to captures.size,
        )
    }

    /** The UTF-8 length of [text] up to [charIndex]. */
    fun byteOffset(
        text: String,
        charIndex: Int,
    ): Int = text.substring(0, charIndex).encodeToByteArray().size

    /** The character index in [text] where the 1-based UTF-8 byte position [bytePosition] falls. */
    fun charIndex(
        text: String,
        bytePosition: Int,
    ): Int {
        var bytes = 0
        var index = 0
        while (index < text.length && bytes < bytePosition - 1) {
            val code = text[index].code
            bytes +=
                when {
                    code < 0x80 -> 1
                    code < 0x800 -> 2
                    text[index].isHighSurrogate() -> 4.also { index++ }
                    else -> 3
                }
            index++
        }
        return index
    }

    private companion object {
        const val MAX_CACHED = 256
    }
}
