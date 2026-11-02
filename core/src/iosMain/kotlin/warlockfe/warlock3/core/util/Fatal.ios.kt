package warlockfe.warlock3.core.util

// Kotlin/Native throws OutOfMemoryError when an allocation fails; a stack overflow is not
// thrown at all, it ends the process.
actual fun Throwable.isFatal(): Boolean = this is OutOfMemoryError
