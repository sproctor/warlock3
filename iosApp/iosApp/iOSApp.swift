import SwiftUI
import Compose
import Sentry

@main
struct iOSApp: App {
    init() {
        // Started here rather than from Kotlin: the Kotlin Sentry SDK's iOS cinterop carries a
        // `-framework Sentry` linker flag that Xcode satisfies through SPM but Gradle cannot when it
        // links the iOS test binary. Using the Cocoa SDK directly keeps `./gradlew check` working.
        // Its crash handler is process-wide, so Kotlin/Native crashes are still reported.
        //
        // Keep the DSN and the release format in step with SentryInit.kt, which does the same job
        // for desktop and Android.
        #if !DEBUG
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String
        SentrySDK.start { options in
            options.dsn = "https://06169c08bd931ba4308dab95573400e2@o4508437273378816.ingest.us.sentry.io/4508437322727424"
            options.releaseName = "ios@\(version ?? "unknown")"
            options.sendDefaultPii = false
        }
        #endif
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
