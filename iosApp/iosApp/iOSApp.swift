import SwiftUI
import Compose
import Sentry

@main
struct iOSApp: App {
    init() {
        // Before anything else, so a crash during startup still gets reported -- but Kotlin only
        // starts the reporter if the user has already agreed to it. On a first launch nothing is
        // sent until they have answered the notice the app shows.
        IosAppContainerProvider.shared.startCrashReporting(reporter: CocoaCrashReporter())
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

/// Crash reporting through the Cocoa SDK, started and stopped by Kotlin's CrashReporting as the user
/// chooses.
///
/// Started here rather than from Kotlin: the Kotlin Sentry SDK's iOS cinterop carries a
/// `-framework Sentry` linker flag that Xcode satisfies through SPM but Gradle cannot when it
/// links the iOS test binary. Using the Cocoa SDK directly keeps `./gradlew check` working.
/// Its crash handler is process-wide, so Kotlin/Native crashes are still reported.
///
/// Keep the DSN and the release format in step with SentryCrashReporter.kt, which does the same job
/// for desktop and Android.
class CocoaCrashReporter: NSObject, CrashReporter {
    func start(onReport: @escaping (String, String) -> Void) {
        // Debug builds never report: reports from someone's working tree are noise.
        #if !DEBUG
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String
        SentrySDK.start { options in
            options.dsn = "https://06169c08bd931ba4308dab95573400e2@o4508437273378816.ingest.us.sentry.io/4508437322727424"
            options.releaseName = "ios@\(version ?? "unknown")"
            options.sendDefaultPii = false
            // Keep a copy of what is sent, so the user can see it in the app.
            options.beforeSend = { event in
                // The SDK fills in a per-install id as the "user"; drop it, so no two reports can
                // be tied to the same person.
                event.user = nil
                let payload = event.serialize()
                if JSONSerialization.isValidJSONObject(payload),
                   let data = try? JSONSerialization.data(withJSONObject: payload),
                   let json = String(data: data, encoding: .utf8) {
                    onReport(event.eventId.sentryIdString, json)
                }
                return event
            }
        }
        #endif
    }

    func stop() {
        #if !DEBUG
        SentrySDK.close()
        #endif
    }
}
