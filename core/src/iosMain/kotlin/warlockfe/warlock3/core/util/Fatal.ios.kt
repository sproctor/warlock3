package warlockfe.warlock3.core.util

// Kotlin/Native ends the process itself on running out of memory, so nothing reaches a catch.
actual fun Throwable.isFatal(): Boolean = false
