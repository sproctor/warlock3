package warlockfe.warlock3.core.sge

/**
 * Parses a Simutronics ".sal" launch file into the credentials needed to connect to the game.
 *
 * A .sal file is a plain text file of KEY=VALUE lines written by the website launcher. Only the
 * GAMEHOST, GAMEPORT, and KEY entries are required to establish a connection; any other entries
 * (GAME, GAMECODE, FULLGAMENAME, etc.) are ignored.
 */
fun parseSalCredentials(content: String): SimuGameCredentials {
    val values =
        content
            .lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) {
                    return@mapNotNull null
                }
                line.substring(0, separator).trim().uppercase() to line.substring(separator + 1).trim()
            }.toMap()

    val host =
        values["GAMEHOST"]?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Missing GAMEHOST in .sal file")
    val port =
        values["GAMEPORT"]?.toIntOrNull()
            ?: throw IllegalArgumentException("Missing or invalid GAMEPORT in .sal file")
    val key =
        values["KEY"]?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Missing KEY in .sal file")

    return SimuGameCredentials(host = host, port = port, key = key)
}
