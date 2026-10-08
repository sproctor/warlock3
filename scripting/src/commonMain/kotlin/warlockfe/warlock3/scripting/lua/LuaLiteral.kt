package warlockfe.warlock3.scripting.lua

/**
 * A Kotlin value as a Lua table constructor or constant, for the bootstrap to `load`: maps become
 * tables keyed by their keys' text, lists and sets become sequences, numbers, booleans and strings
 * are spelled the Lua way, and null is `nil` (and leaves a map entry out). Text rather than a table
 * handle for the reason [jsonToLuaLiteral] gives.
 */
internal fun luaLiteral(value: Any?): String = buildString { appendLuaValue(value) }

private fun StringBuilder.appendLuaValue(value: Any?) {
    when (value) {
        null -> {
            append("nil")
        }

        is Boolean -> {
            append(value)
        }

        is Int, is Long -> {
            append(value)
        }

        is Double -> {
            if (value.isFinite()) append(value) else append("nil")
        }

        is Float -> {
            appendLuaValue(value.toDouble())
        }

        is String -> {
            appendLuaString(value)
        }

        is Map<*, *> -> {
            append('{')
            for ((key, entry) in value) {
                if (entry == null) continue
                append('[')
                when (key) {
                    is Int, is Long -> append(key)
                    else -> appendLuaString(key.toString())
                }
                append("]=")
                appendLuaValue(entry)
                append(',')
            }
            append('}')
        }

        is Iterable<*> -> {
            // Explicit indices, so a null in the middle keeps the positions after it.
            append('{')
            value.forEachIndexed { index, entry ->
                if (entry != null) {
                    append('[').append(index + 1).append("]=")
                    appendLuaValue(entry)
                    append(',')
                }
            }
            append('}')
        }

        else -> {
            appendLuaString(value.toString())
        }
    }
}
