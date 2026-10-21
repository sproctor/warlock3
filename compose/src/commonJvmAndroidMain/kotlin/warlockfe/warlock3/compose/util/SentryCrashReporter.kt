package warlockfe.warlock3.compose.util

import io.sentry.KeyValueCollectionBehavior
import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryPlatformOptions
import warlockfe.warlock3.compose.crash.CrashReporter
import java.io.StringWriter

/**
 * The one Sentry project all three platforms report into. Kept here rather than in the app modules
 * so desktop, Android, and iOS cannot drift apart on DSN or release format.
 */
private const val SENTRY_DSN =
    "https://06169c08bd931ba4308dab95573400e2@o4508437273378816.ingest.us.sentry.io/4508437322727424"

/**
 * Crash reporting through Sentry. [platform] is one of "desktop", "android", or "ios"; it becomes the
 * prefix of the Sentry release ("android@3.1.0"), which is what lets an issue be filtered to one
 * platform and one version.
 *
 * Whether it starts at all is the user's choice (see [warlockfe.warlock3.compose.crash.CrashReporting]).
 * [isRelease] is the build's: a dev/debug build reporting from someone's working tree is noise, so
 * when it is false [start] does nothing.
 */
class SentryCrashReporter(
    private val platform: String,
    private val version: String,
    private val isRelease: Boolean,
) : CrashReporter {
    override fun start(onReport: (eventId: String, json: String) -> Unit) {
        if (!isRelease) return
        // The platform options rather than the multiplatform ones, because only these hand
        // beforeSend the whole event, stack trace included, and the serializer that turns it into
        // what is actually sent.
        Sentry.initWithPlatformOptions { options: SentryPlatformOptions ->
            options.dsn = SENTRY_DSN
            options.release = "$platform@$version"
            // The Sentry project also strips the sending IP and scrubs PII server-side. Turning off
            // every category of automatic collection here keeps the client end of that promise
            // stated in the code. All of them, not just the user: once any Data Collection option
            // is set, the SDK treats each one left unset as its documented default, which is on.
            options.dataCollection.apply {
                userInfo = false
                filePaths = false
                databaseQueryData = false
                cookies = KeyValueCollectionBehavior.off()
                urlQueryParams = KeyValueCollectionBehavior.off()
                httpBodies = emptySet()
                httpHeaders.request = KeyValueCollectionBehavior.off()
                httpHeaders.response = KeyValueCollectionBehavior.off()
                graphql.document = false
                graphql.variables = false
            }
            // Anonymous means anonymous: on desktop the SDK would otherwise name the machine
            // (its hostname) in every report.
            options.isAttachServerName = false
            val serializer = options.serializer
            options.setBeforeSend { event, _ ->
                // Android fills in a per-install id as the "user"; drop it, so no two reports can be
                // tied to the same person.
                event.user = null
                try {
                    val json = StringWriter().also { serializer.serialize(event, it) }.toString()
                    onReport(event.eventId.toString(), json)
                } catch (_: Throwable) {
                    // Keeping a local copy is a courtesy; it must never stop the report itself.
                }
                event
            }
        }
    }

    override fun stop() {
        if (!isRelease) return
        Sentry.close()
    }
}
