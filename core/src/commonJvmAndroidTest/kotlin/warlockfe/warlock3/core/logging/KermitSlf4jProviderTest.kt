package warlockfe.warlock3.core.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KermitSlf4jProviderTest {
    private class Entry(
        val severity: Severity,
        val message: String,
        val tag: String,
        val throwable: Throwable?,
    )

    private class RecordingWriter : LogWriter() {
        val entries = mutableListOf<Entry>()

        override fun log(
            severity: Severity,
            message: String,
            tag: String,
            throwable: Throwable?,
        ) {
            entries += Entry(severity, message, tag, throwable)
        }
    }

    /** Kermit's [Logger] is process-wide state, so put it back the way we found it. */
    private fun recording(
        minSeverity: Severity = Severity.Verbose,
        block: (RecordingWriter) -> Unit,
    ) {
        val previousWriters = Logger.mutableConfig.logWriterList
        val previousSeverity = Logger.mutableConfig.minSeverity
        try {
            val writer = RecordingWriter()
            Logger.setLogWriters(writer)
            Logger.setMinSeverity(minSeverity)
            block(writer)
        } finally {
            Logger.mutableConfig.logWriterList = previousWriters
            Logger.mutableConfig.minSeverity = previousSeverity
        }
    }

    @Test
    fun slf4jResolvesToTheKermitProvider() {
        // Nothing references the provider by name; if this fails, META-INF/services did not make it
        // onto the classpath and slf4j has fallen back to its no-op logger.
        assertIs<KermitLoggerFactory>(LoggerFactory.getILoggerFactory())
        assertIs<KermitSlf4jLogger>(LoggerFactory.getLogger("io.ktor.client.HttpClient"))
    }

    @Test
    fun loggersAreCachedByName() {
        assertSame(LoggerFactory.getLogger("repeated.name"), LoggerFactory.getLogger("repeated.name"))
    }

    @Test
    fun levelsMapOntoKermitSeverities() {
        recording { writer ->
            val logger = LoggerFactory.getLogger("levels")
            logger.trace("t")
            logger.debug("d")
            logger.info("i")
            logger.warn("w")
            logger.error("e")

            assertEquals(
                listOf(Severity.Verbose, Severity.Debug, Severity.Info, Severity.Warn, Severity.Error),
                writer.entries.map { it.severity },
            )
        }
    }

    @Test
    fun theLoggerNameBecomesTheTag() {
        recording { writer ->
            LoggerFactory.getLogger("io.ktor.client.HttpClient").info("hello")

            assertEquals("io.ktor.client.HttpClient", writer.entries.single().tag)
        }
    }

    @Test
    fun placeholdersAreFormatted() {
        recording { writer ->
            LoggerFactory.getLogger("format").info("connecting to {}:{}", "example.com", 1234)

            assertEquals("connecting to example.com:1234", writer.entries.single().message)
        }
    }

    @Test
    fun aTrailingThrowableIsPassedThroughRatherThanFormatted() {
        val boom = IllegalStateException("boom")

        recording { writer ->
            LoggerFactory.getLogger("throwing").error("failed on {}", "attempt 2", boom)

            val entry = writer.entries.single()
            assertEquals("failed on attempt 2", entry.message)
            assertSame(boom, entry.throwable)
        }
    }

    @Test
    fun aBareThrowableWithNoMessageDoesNotBlowUp() {
        // slf4j allows a null message, and a logging call is the last place that should throw.
        val boom = IllegalStateException("boom")

        recording { writer ->
            LoggerFactory.getLogger("messageless").error(null, boom)

            val entry = writer.entries.single()
            assertEquals("", entry.message)
            assertSame(boom, entry.throwable)
        }
    }

    @Test
    fun minSeverityGatesSlf4jLoggingToo() {
        recording(minSeverity = Severity.Info) { writer ->
            val logger = LoggerFactory.getLogger("gated")
            assertFalse(logger.isDebugEnabled)
            assertTrue(logger.isWarnEnabled)

            logger.debug("dropped")
            logger.warn("kept")

            assertEquals(listOf("kept"), writer.entries.map { it.message })
        }
    }

    @Test
    fun aLaterMinSeverityChangeReachesAlreadyCachedLoggers() {
        // Libraries hold onto the logger they fetched at class-init time, which for anything
        // touched during start-up is before the app has configured Kermit.
        val logger = LoggerFactory.getLogger("cached.before.configuration")

        recording(minSeverity = Severity.Warn) {
            assertFalse(logger.isInfoEnabled)
        }
        recording(minSeverity = Severity.Verbose) {
            assertTrue(logger.isInfoEnabled)
        }
    }
}
