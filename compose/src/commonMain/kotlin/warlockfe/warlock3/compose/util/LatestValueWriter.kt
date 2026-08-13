package warlockfe.warlock3.compose.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Writes the latest value it has been given, one write at a time.
 *
 * The pending value is read inside the lock rather than captured when the write was asked for, so a
 * write that queues behind another still stores the newest value and the last write to land can
 * never be a stale one. That is the failure this exists to rule out: for auto-saved state, two
 * writes that finish out of order leave the older version in the store with nothing to correct it,
 * and the user sees their most recent change quietly reverted.
 *
 * Nothing here tracks what has already been written, so a value can be written twice - [save] is
 * expected to be an idempotent upsert. That is deliberate: remembering what landed would let a
 * failed write drop a value that a later flush would otherwise have stored.
 */
internal class LatestValueWriter<T : Any>(
    private val save: suspend (T) -> Unit,
) {
    private val mutex = Mutex()
    private var pending: T? = null

    /** Records [value] as the latest and writes it, waiting for any write already in flight. */
    suspend fun write(value: T) {
        pending = value
        flush()
    }

    /** Writes the latest value, if one has been recorded. */
    suspend fun flush() {
        mutex.withLock {
            val value = pending ?: return
            save(value)
        }
    }
}
