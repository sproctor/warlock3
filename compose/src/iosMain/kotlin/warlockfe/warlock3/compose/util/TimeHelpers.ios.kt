package warlockfe.warlock3.compose.util

import kotlin.time.Instant

actual fun Instant.toTimeString(): String {
    return toString() // TODO: unify this with other platforms and use kotlinx datetime methods
}