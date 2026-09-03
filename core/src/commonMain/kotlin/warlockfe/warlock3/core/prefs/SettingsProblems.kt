package warlockfe.warlock3.core.prefs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Something that has gone wrong with the user's settings, in the words they read.
 *
 * [reason] is what the storage layer said, near enough verbatim (see [describeForUser]) -- for the
 * two crashes this was written for, "SQLiteException: Error code: 1034, message: disk I/O error" and
 * "FileSystemException: .../warlock-v22.db.lck: Read-only file system". Keeping its own words is the
 * point: it is the part that says which of "the disk is full", "the volume went read-only", and
 * "something else entirely" the user is dealing with, and no paraphrase of ours would be as useful
 * to paste into a bug report.
 */
data class SettingsProblem(
    val headline: String,
    val reason: String,
    val advice: String,
) {
    val message: String
        get() = "$headline\n\n$reason\n\n$advice"

    companion object {
        /** One write did not reach the disk. [what] is a noun phrase: "your window layout". */
        fun saveFailed(
            what: String,
            reason: String,
            settingsLocation: String,
        ) = SettingsProblem(
            headline = "Warlock could not save $what.",
            reason = reason,
            advice =
                "Changes made now will be forgotten when Warlock closes. Settings are kept in " +
                    "$settingsLocation; check that the disk is not full and that Warlock can write there.",
        )

        /** Settings that are on disk but could not be read -- see [SettingsUnreadableException]. */
        fun unreadable(
            what: String,
            reason: String,
            settingsLocation: String,
        ) = SettingsProblem(
            headline = "Warlock could not read $what.",
            reason = reason,
            advice =
                "Warlock will close rather than start with empty settings and save them over what " +
                    "is on disk. Settings are kept in $settingsLocation; check that the files there " +
                    "are readable and undamaged.",
        )
    }
}

/**
 * Settings that exist on disk but cannot be read. Fatal on purpose: the app must stop rather than
 * carry on.
 *
 * The alternative is worse than not starting. Every store here treats "no file" as "no settings
 * yet", so a file that cannot be read looks exactly like a fresh install -- the app comes up with
 * empty highlights and default macros, and the first save writes that emptiness over settings that
 * were never actually gone. A user whose disk has a bad sector or who left a typo in a
 * hand-edited TOML file gets to fix it and start again; one whose settings were quietly replaced
 * with defaults does not.
 *
 * Only the startup read throws. On the write path there is no longer a launch to abort, so those
 * reads keep their older log-and-continue behavior.
 */
class SettingsUnreadableException(
    val problem: SettingsProblem,
) : Exception(problem.message)

/**
 * Where a settings *write* failure goes so that the person using Warlock finds out about it.
 *
 * A save is launched and never awaited -- a `viewModelScope.launch` reacting to a click -- so there
 * is no caller to throw to. An escaping exception went to the thread's uncaught handler instead,
 * which turned a full disk into a fatal-level crash report the user could not read (DESKTOP-3X).
 * The person whose settings are being dropped is the one who can do something about it, so they are
 * who gets told. Unlike an unreadable database this is not fatal: the settings on disk are intact
 * and the app is still usable, it just cannot record anything new.
 *
 * Silence after a dismissal is the point of [silenced]. A disk that has stopped taking writes fails
 * *every* save -- every resize, every window toggle -- so without it, dismissing the report would
 * only summon the next one. Once the user has seen it, nothing more is said until a save succeeds
 * again, which is also what makes a problem that comes back get reported again.
 */
class SettingsProblems(
    private val settingsLocation: String,
) {
    private val _current = MutableStateFlow<SettingsProblem?>(null)

    /** The problem the user has not dismissed yet; null when there is nothing to tell them. */
    val current: StateFlow<SettingsProblem?> = _current.asStateFlow()

    private val silenced = MutableStateFlow(false)

    fun recordSaveFailure(
        what: String,
        reason: String,
    ) {
        if (silenced.value) return
        // Keep the first failure of an episode rather than the newest: it is the one whose timing
        // the user can place. Reading `silenced` above and updating here is not one atomic step, but
        // the worst a race can do is show a report we would have held back.
        _current.update { current ->
            current ?: SettingsProblem.saveFailed(
                what = what,
                reason = reason,
                settingsLocation = settingsLocation,
            )
        }
    }

    /** A save worked, so whatever went wrong is over and the next failure is worth reporting again. */
    fun recordSuccess() {
        if (silenced.value) silenced.value = false
    }

    /** The user has read it. Stay quiet until saving works again. */
    fun dismiss() {
        _current.value = null
        silenced.value = true
    }
}

/**
 * The line to show the user for [this]: the deepest cause in the chain, named by type.
 *
 * The outermost message is usually the least useful one. Room reports a database it could not open
 * as "Unable to open database '...'. Was a proper path / name used in Room's database builder?",
 * which points at a configuration mistake the user did not make; three causes down sits
 * "warlock-v22.db.lck: Read-only file system", which is what actually tells them what to fix.
 *
 * The type goes in front because the deepest message is not always a sentence. A JVM
 * `FileSystemException` carries its reason ("...db.lck: Read-only file system") but an
 * `AccessDeniedException` for the same file carries only the path, and a bare path tells the user
 * nothing at all. Prefixing costs a word of jargon in the cases that read well and rescues the ones
 * that do not.
 */
fun Throwable.describeForUser(): String {
    var deepest: Throwable = this
    // Bounded rather than `while (cause != null)`: a self-referential or cyclic cause chain is rare
    // but it would hang the app on the way to reporting an error, which is a poor trade.
    repeat(MAX_CAUSE_DEPTH) {
        val cause = deepest.cause ?: return@repeat
        if (cause !== deepest) deepest = cause
    }
    val name = deepest::class.simpleName ?: this::class.simpleName ?: "Error"
    val detail = deepest.message ?: message
    return if (detail.isNullOrBlank()) name else "$name: $detail"
}

private const val MAX_CAUSE_DEPTH = 16
