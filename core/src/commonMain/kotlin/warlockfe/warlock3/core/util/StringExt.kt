package warlockfe.warlock3.core.util

import java.util.UUID

fun String.toUuidOrNull(): UUID? {
    return try {
        UUID.fromString(this)
    } catch (e: IllegalArgumentException) {
        null
    }
}

fun String.splitFirstWord(): Pair<String, String?> {
    val list = trim().split(Regex("[ \t]+"), limit = 2)
    return Pair(list[0], list.getOrNull(1))
}