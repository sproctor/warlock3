package warlockfe.warlock3.compose.crash

/**
 * What the user is told about crash reporting, shared by the desktop and mobile UIs so the promise
 * reads the same everywhere. If what the SDK collects changes (see SentryCrashReporter.kt and iOSApp.swift),
 * this has to change with it.
 */
object CrashReportingText {
    const val NOTICE_TITLE = "Send crash reports?"

    const val NOTICE_BODY =
        "If Warlock crashes, it can send a crash report to help fix the problem.\n\n" +
            "\u2022 Nothing intentionally identifies you: no account, character, password, or computer name.\n" +
            "\u2022 Your information will never be sold.\n" +
            "\u2022 Reports are sent to Sentry anonymously and used only to find and fix crashes.\n" +
            "\u2022 You can change this, and read a copy of each report, under Settings > Crash reports."

    const val ACCEPT = "Send crash reports"
    const val DECLINE = "Don't send"

    const val SETTING_LABEL = "Send anonymous crash reports"

    const val SETTINGS_DESCRIPTION =
        "When Warlock crashes, it can send an anonymous report of the error so the problem can be " +
            "diagnosed and fixed. Reports are used for nothing else. A copy of each report is saved here, " +
            "exactly as it was handed to Sentry for sending."

    const val NO_REPORTS = "No crash reports have been saved."

    const val DELETE_ALL = "Delete these copies"

    const val DELETE_NOTE = "Deleting the copies here does not delete reports Sentry has already received."
}
