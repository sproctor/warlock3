package warlockfe.warlock3.core.logging

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import org.slf4j.ILoggerFactory
import org.slf4j.IMarkerFactory
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.BasicMDCAdapter
import org.slf4j.helpers.BasicMarkerFactory
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.helpers.MessageFormatter
import org.slf4j.spi.MDCAdapter
import org.slf4j.spi.SLF4JServiceProvider
import java.util.concurrent.ConcurrentHashMap

/**
 * Routes slf4j into Kermit, so that libraries logging through slf4j land wherever the app has
 * pointed [Logger] and obey the same [Logger.setMinSeverity] as our own logging.
 *
 * We do not log through slf4j ourselves, but we cannot avoid it either: ktor declares slf4j-api
 * across all of its modules, and dbus-java pulls it in behind FileKit's file dialogs on Linux.
 * Without a provider on the classpath slf4j 2.x prints a "No SLF4J providers were found" banner
 * and silently drops everything those libraries have to say. Binding it here instead of to
 * slf4j-simple means one severity setting and one destination for the whole app rather than two.
 *
 * Registered via `META-INF/services/org.slf4j.spi.SLF4JServiceProvider`; nothing references it
 * by name, so it is loaded reflectively by [org.slf4j.LoggerFactory].
 */
class KermitSlf4jProvider : SLF4JServiceProvider {
    private val loggerFactory = KermitLoggerFactory()
    private val markerFactory = BasicMarkerFactory()

    // Markers and MDC are accepted and then ignored: nothing in the app sets them, and Kermit has
    // nowhere to put them. They still need working implementations rather than nulls, because
    // MDC and MarkerFactory hand these straight to callers - kotlinx-coroutines-slf4j's MDCContext
    // rides in on ktor and would touch the adapter if anyone ever used it.
    private val mdcAdapter = BasicMDCAdapter()

    override fun getLoggerFactory(): ILoggerFactory = loggerFactory

    override fun getMarkerFactory(): IMarkerFactory = markerFactory

    override fun getMDCAdapter(): MDCAdapter = mdcAdapter

    override fun getRequestedApiVersion(): String = REQUESTED_API_VERSION

    override fun initialize() {
        // Kermit needs no start-up; Logger is usable from its defaults before the app configures it.
    }

    private companion object {
        /**
         * The slf4j API series this provider is written against, which is what
         * `LoggerFactory.versionSanityCheck()` compares against its own `{"2.0"}` compatibility
         * list - not the version of slf4j-api we happen to resolve. Mismatches only warn, but they
         * warn on stderr at first use, which is exactly the noise this provider exists to remove.
         */
        const val REQUESTED_API_VERSION = "2.0.99"
    }
}

internal class KermitLoggerFactory : ILoggerFactory {
    private val loggers = ConcurrentHashMap<String, org.slf4j.Logger>()

    override fun getLogger(name: String): org.slf4j.Logger = loggers.computeIfAbsent(name) { KermitSlf4jLogger(it) }
}

/**
 * One slf4j logger, forwarding to the global Kermit [Logger].
 *
 * [LegacyAbstractLogger] does the tedious half of the `org.slf4j.Logger` interface for us: it
 * funnels all thirty-odd overloads through [handleNormalizedLoggingCall], having already checked
 * the matching `isXxxEnabled()`, so arguments are only formatted for messages that will be logged.
 */
internal class KermitSlf4jLogger(
    name: String,
) : LegacyAbstractLogger() {
    init {
        // AbstractLogger.getName() reads this protected field.
        this.name = name
    }

    override fun isTraceEnabled(): Boolean = isEnabled(Severity.Verbose)

    override fun isDebugEnabled(): Boolean = isEnabled(Severity.Debug)

    override fun isInfoEnabled(): Boolean = isEnabled(Severity.Info)

    override fun isWarnEnabled(): Boolean = isEnabled(Severity.Warn)

    override fun isErrorEnabled(): Boolean = isEnabled(Severity.Error)

    /** Only used by back-ends that walk the stack for the caller's location. Kermit does not. */
    override fun getFullyQualifiedCallerName(): String? = null

    override fun handleNormalizedLoggingCall(
        level: Level,
        marker: Marker?,
        messagePattern: String?,
        arguments: Array<out Any?>?,
        throwable: Throwable?,
    ) {
        Logger.log(
            severity = level.toSeverity(),
            tag = name,
            throwable = throwable,
            // basicArrayFormat returns null back for a null pattern, which slf4j permits: callers
            // may log a bare throwable. Kermit wants a message, and a logging call is the last
            // place that should be able to throw.
            message = MessageFormatter.basicArrayFormat(messagePattern, arguments).orEmpty(),
        )
    }
}

/**
 * The severity is read from the global Kermit config on every call rather than captured, so that
 * a later [Logger.setMinSeverity] applies to loggers a library has already cached.
 */
private fun isEnabled(severity: Severity): Boolean = Logger.config.minSeverity <= severity

private fun Level.toSeverity(): Severity =
    when (this) {
        Level.TRACE -> Severity.Verbose
        Level.DEBUG -> Severity.Debug
        Level.INFO -> Severity.Info
        Level.WARN -> Severity.Warn
        Level.ERROR -> Severity.Error
    }
