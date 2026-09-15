package warlockfe.warlock3.scripting.lua

import com.seanproctor.lua.LuaValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * JSON as a Lua table constructor: `{"hp":10,"tags":["a"]}` becomes `{["hp"]=10,["tags"]={"a",},}`,
 * ready for `load("return " .. literal)`. Null for anything that is not JSON, blank included.
 *
 * Text rather than a table handle because every [LuaValue.Table] the host makes stays pinned in
 * the Lua registry until the state closes, and a MUD sends vitals for as long as one plays.
 */
internal fun jsonToLuaLiteral(json: String): String? {
    if (json.isBlank()) return null
    val element = runCatching { Json.parseToJsonElement(json) }.getOrNull() ?: return null
    return buildString { appendLuaLiteral(element) }
}

private fun StringBuilder.appendLuaLiteral(element: JsonElement) {
    when (element) {
        is JsonNull -> {
            append("nil")
        }

        // Numbers and booleans are spelled the same in both languages.
        is JsonPrimitive -> {
            if (element.isString) appendLuaString(element.content) else append(element.content)
        }

        is JsonArray -> {
            append('{')
            element.forEach {
                appendLuaLiteral(it)
                append(',')
            }
            append('}')
        }

        is JsonObject -> {
            append('{')
            for ((key, value) in element) {
                // A nil value leaves the key absent either way.
                if (value is JsonNull) continue
                append('[')
                appendLuaString(key)
                append("]=")
                appendLuaLiteral(value)
                append(',')
            }
            append('}')
        }
    }
}

/** [text] as a quoted Lua string literal. */
internal fun StringBuilder.appendLuaString(text: String) {
    append('"')
    for (c in text) {
        when {
            c == '"' -> append("\\\"")

            c == '\\' -> append("\\\\")

            c == '\n' -> append("\\n")

            c == '\r' -> append("\\r")

            c == '\t' -> append("\\t")

            // Other control characters (DEL included), as decimal escapes.
            c.code < 0x20 || c.code == 0x7F -> append('\\').append(c.code.toString().padStart(3, '0'))

            else -> append(c)
        }
    }
    append('"')
}

/**
 * A Lua value as JSON, for sending to the server: a table whose keys are exactly 1..n is an
 * array, any other table an object with its keys as strings, and a function null.
 */
internal fun LuaValue.toJson(): JsonElement =
    when (this) {
        LuaValue.Nil -> JsonNull
        is LuaValue.Bool -> JsonPrimitive(value)
        is LuaValue.Integer -> JsonPrimitive(value)
        is LuaValue.Number -> JsonPrimitive(value)
        is LuaValue.Str -> JsonPrimitive(value)
        is LuaValue.Table -> tableToJson()
        is LuaValue.Function -> JsonNull
    }

private fun LuaValue.Table.tableToJson(): JsonElement {
    val map = toMap()
    if (map.isEmpty()) return JsonObject(emptyMap())
    val indices = map.keys.map { (it as? LuaValue.Integer)?.value }
    if (indices.all { it != null } && indices.filterNotNull().sorted() == (1L..map.size).toList()) {
        return JsonArray((1L..map.size).map { map.getValue(LuaValue.Integer(it)).toJson() })
    }
    return JsonObject(map.entries.associate { (key, value) -> key.keyString() to value.toJson() })
}

private fun LuaValue.keyString(): String =
    when (this) {
        is LuaValue.Str -> value
        is LuaValue.Integer -> value.toString()
        is LuaValue.Number -> value.toString()
        is LuaValue.Bool -> value.toString()
        else -> toString()
    }
