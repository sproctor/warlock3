package warlockfe.warlock3.scripting.util

enum class ScriptLoggingLevel(val level: Int) {
    VERBOSE(0),
    DEBUG(10),
    INFO(20),
    WARN(30),
    ERROR(40),
    CRITICAL(50);

    companion object {
        fun fromString(level: String): ScriptLoggingLevel? {
            entries.forEach { entry ->
                if (entry.name.equals(level, ignoreCase = true)) {
                    return entry
                }
            }
            return null
        }
    }
}