package cc.warlock.warlock3.core.util

import java.util.*

class CaseInsensitiveMap<T> constructor() : TreeMap<String, T>(String.CASE_INSENSITIVE_ORDER) {
    constructor(values: Map<String, T>) : this() {
        putAll(values)
    }

    constructor(vararg pairs: Pair<String, T>) : this() {
        putAll(pairs)
    }
}

fun <T> Map<String, T>.toCaseInsensitiveMap(): Map<String, T> {
    return CaseInsensitiveMap(this)
}