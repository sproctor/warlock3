package warlockfe.warlock3.compose.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Writes the latest value it has been given, one write at a time.
 *
 * The pending value is read inside the lock rather than captured when the write was asked for, so a
 * write that queues behind another still stores the newest value and the last write to land can
 * never be a stale one. That is the failure this exists to rule out: for auto-saved state, two
 * writes that finish out of order leave the older version in the store with nothing to correct it,
 * and the user sees their most recent change quietly reverted.
 *
 * A value is dropped once it has been written, unless a newer one was recorded meanwhile, so a
 * flush with nothing new writes nothing. A failed write keeps it, for the next flush to retry.
 *
 * [record] and [flush] may be called from any thread, but [flush] runs [save] on the caller's, so a
 * value that has to be read on a particular thread has to be flushed there.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class LatestValueWriter<T : Any>(
    private val save: suspend (T) -> Unit,
) {
    // Each recorded value gets a box of its own, so a flush clears exactly the value it wrote, even
    // when the same value is recorded again while it is being written.
    private class Pending<T>(
        val value: T,
    )

    private val mutex = Mutex()
    private val pending = AtomicReference<Pending<T>?>(null)

    /** Records [value] as the latest and writes it, waiting for any write already in flight. */
    suspend fun write(value: T) {
        record(value)
        flush()
    }

    /** Records [value] as the latest without writing it, for a later [write] or [flush] to write. */
    fun record(value: T) {
        pending.store(Pending(value))
    }

    /** Writes the latest value, if one has been recorded since the last write. */
    suspend fun flush() {
        mutex.withLock {
            val latest = pending.load() ?: return
            save(latest.value)
            // Unless a newer value arrived while this one was being written.
            pending.compareAndSet(latest, null)
        }
    }
}
