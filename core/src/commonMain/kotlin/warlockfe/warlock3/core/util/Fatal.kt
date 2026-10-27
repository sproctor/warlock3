package warlockfe.warlock3.core.util

/**
 * Whether this is an error the runtime cannot recover from (out of memory, a stack overflow),
 * which should be left to end the app rather than caught and reported like any failure.
 */
expect fun Throwable.isFatal(): Boolean
