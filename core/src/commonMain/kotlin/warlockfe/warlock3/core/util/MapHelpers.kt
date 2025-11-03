package warlockfe.warlock3.core.util

import io.ktor.util.CaseInsensitiveMap

fun <T: Any> CaseInsensitiveMap(vararg pairs: Pair<String, T>): CaseInsensitiveMap<T> {
    return CaseInsensitiveMap<T>()
        .apply { putAll(pairs) }
}

fun <T: Any> Map<String, T>.toCaseInsensitiveMap(): CaseInsensitiveMap<T> {
    return CaseInsensitiveMap<T>().apply {
        putAll(this@toCaseInsensitiveMap)
    }
}

fun <T> Map<String, T>.getIgnoringCase(key: String): T? {
    entries.forEach { entry ->
        if (entry.key.equals(key, true)) {
            return entry.value
        }
    }
    return null
}
