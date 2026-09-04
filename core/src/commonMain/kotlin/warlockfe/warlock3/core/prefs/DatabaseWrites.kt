package warlockfe.warlock3.core.prefs

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

private val logger = Logger.withTag("DatabaseWrites")

/**
 * Runs a settings write against SQLite, holding it open across cancellation and reporting a failure
 * to store it to [problems] -- and so to the user -- instead of throwing.
 *
 * Both halves matter, for different reasons. [NonCancellable] is why a setting saved as its screen
 * closes still lands: the write is started from a scope that is about to be cancelled, and without
 * this it would be abandoned mid-transaction. Catching the failure is because these writes are
 * launched, not awaited -- almost always from a `viewModelScope.launch` reacting to a click -- so
 * there is no caller to hand an exception back to. What escapes a bare `launch` goes to the thread's
 * uncaught handler instead, which reports it as a fatal crash to a service the user cannot read,
 * while the user themselves is told nothing and goes on making changes that are being dropped. See
 * [SettingsProblems] for what happens to it instead.
 *
 * [what] names what was being saved, as the noun phrase the user reads: "your window layout", not a
 * column name. The exception itself, which carries the detail a developer needs, goes to the log.
 */
suspend fun persistToDatabase(
    problems: SettingsProblems,
    what: String,
    write: suspend () -> Unit,
) {
    withContext(NonCancellable) {
        try {
            write()
            problems.recordSuccess()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.e(e) { "Failed to save $what" }
            problems.recordSaveFailure(what = what, reason = e.describeForUser())
        }
    }
}
