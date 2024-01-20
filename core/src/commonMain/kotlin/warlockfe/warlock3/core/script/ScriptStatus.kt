package warlockfe.warlock3.core.script

enum class ScriptStatus {
    NotStarted,
    Running,
    Suspended,
    Stopped;

    override fun toString(): String {
        return when (this) {
            NotStarted -> "not started"
            Running -> "running"
            Suspended -> "paused"
            Stopped -> "stopped"
        }
    }
}