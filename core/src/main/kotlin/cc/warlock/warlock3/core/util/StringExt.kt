package cc.warlock.warlock3.core.util

import java.util.UUID

fun String.toUuidOrNull(): UUID? {
    return try {
        UUID.fromString(this)
    } catch (e: IllegalArgumentException) {
        null
    }
}