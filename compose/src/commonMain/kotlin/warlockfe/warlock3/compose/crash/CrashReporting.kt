package warlockfe.warlock3.compose.crash

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The crash-reporting SDK, as far as the rest of the app needs to know it. Implemented per platform:
 * [SentryCrashReporter] on desktop and Android, and in Swift on iOS, where the Cocoa SDK is started
 * from `iOSApp.swift`.
 */
interface CrashReporter {
    /**
     * Start reporting. Each report is passed to [onReport] as the JSON the SDK is about to send, just
     * before it is sent, so the user can see exactly what left their machine.
     */
    fun start(onReport: (eventId: String, json: String) -> Unit)

    fun stop()
}

/** A crash report the app sent, as kept on disk for the user to look at. */
data class CrashReport(
    val id: String,
    val time: Instant,
    val summary: String,
    val path: Path,
)

/** When the report was sent, in local time, to the minute. */
fun CrashReport.displayTime(): String {
    val local = time.toLocalDateTime(TimeZone.currentSystemDefault())

    fun Int.pad() = toString().padStart(2, '0')
    return "${local.year}-${local.month.number.pad()}-${local.day.pad()} ${local.hour.pad()}:${local.minute.pad()}"
}

/**
 * Whether the user has agreed to send crash reports, and the local copies of the ones that were sent.
 *
 * The answer lives in its own small file rather than with the rest of the settings: it has to be
 * read before anything else starts, so that a crash while loading the settings can still be
 * reported, and so it cannot depend on the settings being readable.
 *
 * Nothing is sent until the user has said yes. [consent] is null until they have answered, which is
 * what the app shell uses to decide whether to ask.
 */
class CrashReporting(
    configDir: String,
    dataDir: String,
    private val fileSystem: FileSystem,
    private val reporter: CrashReporter,
) {
    private val logger = Logger.withTag("CrashReporting")
    private val consentFile = Path(configDir, CONSENT_FILE)
    private val reportsDir = Path(dataDir, REPORTS_DIR)

    private val _consent = MutableStateFlow(readConsent())

    /** True to send reports, false to not, null if the user has not been asked yet. */
    val consent: StateFlow<Boolean?> = _consent.asStateFlow()

    private val _reports = MutableStateFlow<List<CrashReport>>(emptyList())

    /** The reports kept on disk, newest first. Call [refreshReports] to (re)load them. */
    val reports: StateFlow<List<CrashReport>> = _reports.asStateFlow()

    private var started = false

    /** Start the reporter if, and only if, the user has already agreed to it. */
    fun startIfConsented() {
        if (_consent.value == true) start()
    }

    fun setConsent(enabled: Boolean) {
        _consent.value = enabled
        try {
            consentFile.parent?.let { fileSystem.createDirectories(it) }
            fileSystem.sink(consentFile).buffered().use {
                it.writeString(if (enabled) ENABLED else DISABLED)
            }
        } catch (e: Exception) {
            // Still honoured for this session; they will be asked again next launch.
            logger.e(e) { "Could not save the crash reporting choice" }
        }
        if (enabled) start() else stop()
    }

    fun refreshReports() {
        _reports.value =
            try {
                if (!fileSystem.exists(reportsDir)) {
                    emptyList()
                } else {
                    fileSystem
                        .list(reportsDir)
                        .filter { it.name.endsWith(REPORT_SUFFIX) }
                        .mapNotNull { readReportHeader(it) }
                        .sortedByDescending { it.time }
                }
            } catch (e: Exception) {
                logger.e(e) { "Could not list crash reports" }
                emptyList()
            }
    }

    fun readReport(report: CrashReport): String =
        try {
            fileSystem.source(report.path).buffered().use { it.readString() }
        } catch (e: Exception) {
            "Could not read this report: ${e.message}"
        }

    fun deleteReports() {
        try {
            if (fileSystem.exists(reportsDir)) {
                fileSystem.list(reportsDir).forEach { fileSystem.delete(it, mustExist = false) }
            }
        } catch (e: Exception) {
            logger.e(e) { "Could not delete crash reports" }
        }
        refreshReports()
    }

    private fun start() {
        if (started) return
        started = true
        reporter.start(::saveReport)
    }

    private fun stop() {
        if (!started) return
        started = false
        reporter.stop()
    }

    /**
     * Keep a copy of a report as it is sent. Called from the crash handler itself, possibly on a
     * thread that is about to die, so it does as little as it can and never throws.
     */
    fun saveReport(
        eventId: String,
        json: String,
    ) {
        try {
            fileSystem.createDirectories(reportsDir)
            val now = Clock.System.now().toEpochMilliseconds()
            val text =
                try {
                    prettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(json))
                } catch (_: Exception) {
                    json
                }
            val safeId = eventId.filter { it.isLetterOrDigit() }.ifEmpty { "report" }
            fileSystem.sink(Path(reportsDir, "$now-$safeId$REPORT_SUFFIX")).buffered().use {
                it.writeString(text)
            }
            prune()
        } catch (_: Throwable) {
            // A crash report that fails to save locally must not get in the way of it being sent.
        }
        refreshReports()
    }

    private fun prune() {
        fileSystem
            .list(reportsDir)
            .filter { it.name.endsWith(REPORT_SUFFIX) }
            .sortedByDescending { it.name.substringBefore('-').toLongOrNull() ?: 0L }
            .drop(MAX_REPORTS)
            .forEach { fileSystem.delete(it, mustExist = false) }
    }

    private fun readReportHeader(path: Path): CrashReport? {
        val millis = path.name.substringBefore('-').toLongOrNull() ?: return null
        val id = path.name.substringAfter('-').removeSuffix(REPORT_SUFFIX)
        val summary =
            try {
                summarize(fileSystem.source(path).buffered().use { it.readString() })
            } catch (_: Exception) {
                null
            }
        return CrashReport(
            id = id,
            time = Instant.fromEpochMilliseconds(millis),
            summary = summary ?: "Crash report",
            path = path,
        )
    }

    private fun readConsent(): Boolean? =
        try {
            if (fileSystem.exists(consentFile)) {
                when (
                    fileSystem
                        .source(consentFile)
                        .buffered()
                        .use { it.readString() }
                        .trim()
                ) {
                    ENABLED -> true
                    DISABLED -> false
                    else -> null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            logger.e(e) { "Could not read the crash reporting choice" }
            null
        }

    companion object {
        private const val CONSENT_FILE = "crash-reporting.txt"
        private const val REPORTS_DIR = "crash-reports"
        private const val REPORT_SUFFIX = ".json"
        private const val ENABLED = "enabled"
        private const val DISABLED = "disabled"
        private const val MAX_REPORTS = 25

        private val prettyJson = Json { prettyPrint = true }

        /** One line describing a report: the exception that was thrown, or its message. */
        internal fun summarize(json: String): String? {
            val event = Json.parseToJsonElement(json) as? JsonObject ?: return null
            // Sentry lists chained exceptions oldest first, so the last is the one that surfaced.
            val exception =
                ((event["exception"] as? JsonObject)?.get("values") as? JsonArray)
                    ?.lastOrNull() as? JsonObject
            if (exception != null) {
                val type = exception.string("type")
                val value = exception.string("value")
                return listOfNotNull(type, value).joinToString(": ").ifEmpty { null }
            }
            val message = event["message"]
            return when (message) {
                is JsonObject -> message.string("formatted") ?: message.string("message")
                is JsonPrimitive -> message.contentOrNull
                else -> null
            }
        }

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    }
}
