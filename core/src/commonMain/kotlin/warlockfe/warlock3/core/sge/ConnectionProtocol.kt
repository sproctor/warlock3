package warlockfe.warlock3.core.sge

/** How a saved connection reaches its game. */
enum class ConnectionProtocol(
    /** The value written to `connections.toml`. */
    val configValue: String,
) {
    /** A Simutronics game: log in through SGE (or MUD Mobile) and speak the Wrayth protocol. */
    SGE("sge"),

    /** A plain telnet MUD at a host and port, with ANSI color and no login step of our own. */
    TELNET("telnet"),
    ;

    companion object {
        /** The protocol a config value names; anything unrecognised (including the absent value of older files) is [SGE]. */
        fun fromConfig(value: String?): ConnectionProtocol = entries.firstOrNull { it.configValue == value } ?: SGE
    }
}
