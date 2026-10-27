package warlockfe.warlock3.scripting.wsl

import kotlin.concurrent.atomics.AtomicReference

/**
 * A list a fake appends to from whichever thread a script is on, while the test reads it from its
 * own: each read is a snapshot, so polling it while the script writes is safe.
 */
class Recorded<T> {
    private val items = AtomicReference<List<T>>(emptyList())

    fun add(item: T) {
        while (true) {
            val current = items.load()
            if (items.compareAndSet(current, current + item)) return
        }
    }

    fun snapshot(): List<T> = items.load()
}
