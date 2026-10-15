package warlockfe.warlock3.compose.crash

import kotlinx.io.files.SystemFileSystem
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrashReportingTest {
    private lateinit var dir: Path

    private class FakeReporter : CrashReporter {
        var running = false
        var onReport: ((String, String) -> Unit)? = null

        override fun start(onReport: (eventId: String, json: String) -> Unit) {
            running = true
            this.onReport = onReport
        }

        override fun stop() {
            running = false
        }
    }

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("crash-reporting-test")
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun crashReporting(reporter: CrashReporter) =
        CrashReporting(
            configDir = dir.resolve("config").toString(),
            dataDir = dir.resolve("data").toString(),
            fileSystem = SystemFileSystem,
            reporter = reporter,
        )

    @Test
    fun nothingStartsUntilTheUserAgrees() {
        val reporter = FakeReporter()
        val crashReporting = crashReporting(reporter)
        assertNull(crashReporting.consent.value)

        crashReporting.startIfConsented()
        assertEquals(false, reporter.running)

        crashReporting.setConsent(true)
        assertTrue(reporter.running)

        crashReporting.setConsent(false)
        assertEquals(false, reporter.running)
    }

    @Test
    fun theChoiceSurvivesARestart() {
        crashReporting(FakeReporter()).setConsent(true)

        val reporter = FakeReporter()
        val restarted = crashReporting(reporter)
        assertEquals(true, restarted.consent.value)
        restarted.startIfConsented()
        assertTrue(reporter.running)

        restarted.setConsent(false)
        assertEquals(false, crashReporting(FakeReporter()).consent.value)
    }

    @Test
    fun sentReportsAreKeptToRead() {
        val reporter = FakeReporter()
        val crashReporting = crashReporting(reporter)
        crashReporting.setConsent(true)

        val json =
            """{"event_id":"abc123","exception":{"values":[""" +
                """{"type":"IOException","value":"inner"},""" +
                """{"type":"IllegalStateException","value":"it broke"}]}}"""
        reporter.onReport!!("abc123", json)

        val report = crashReporting.reports.value.single()
        assertEquals("abc123", report.id)
        assertEquals("IllegalStateException: it broke", report.summary)
        assertTrue(crashReporting.readReport(report).contains("\"it broke\""))

        crashReporting.deleteReports()
        assertTrue(crashReporting.reports.value.isEmpty())
    }

    @Test
    fun summarizesAMessageEvent() {
        assertEquals(
            "Something odd",
            CrashReporting.summarize("""{"message":{"formatted":"Something odd"}}"""),
        )
    }
}
