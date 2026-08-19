package warlockfe.warlock3.compose.util

import io.sentry.kotlin.multiplatform.Sentry

/**
 * The one Sentry project all three platforms report into. Kept here rather than in the app modules
 * so desktop, Android, and iOS cannot drift apart on DSN or release format.
 */
private const val SENTRY_DSN =
    "https://06169c08bd931ba4308dab95573400e2@o4508437273378816.ingest.us.sentry.io/4508437322727424"

/**
 * Start crash reporting. [platform] is one of "desktop", "android", or "ios"; it becomes the prefix
 * of the Sentry release ("android@3.1.0"), which is what lets an issue be filtered to one platform
 * and one version.
 *
 * Callers decide when *not* to call this: a dev/debug build reporting from someone's working tree is
 * noise, so every entry point gates it on being a real release.
 */
fun initializeSentry(
    platform: String,
    version: String,
) {
    Sentry.init { options ->
        options.dsn = SENTRY_DSN
        options.release = "$platform@$version"
        // The Sentry project also strips the sending IP and scrubs PII server-side. Setting this
        // explicitly (it is the default) keeps the client end of that promise stated in the code,
        // so a future SDK default or an edit here can't quietly start attaching user data.
        options.sendDefaultPii = false
    }
}
