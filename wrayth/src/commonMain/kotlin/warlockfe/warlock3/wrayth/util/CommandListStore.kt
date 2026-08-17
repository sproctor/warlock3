package warlockfe.warlock3.wrayth.util

import co.touchlab.kermit.Logger
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readLine
import kotlinx.io.writeString

/**
 * The command list a server sends in response to `_menu update`, kept between runs.
 *
 * The list is large - a real GemStone connection sends around 600 entries in chunks of 300, and the
 * real client's own cache has grown past a thousand - and it is the same list every login. A server
 * only needs to send what a client is missing, and says which list it just sent with a serial in
 * `<cmdtimestamp>`. Hand that serial back on the next `_menu update` and it knows where to start.
 *
 * Shaped like the protocol, one `<cli>` per line, because the real client's own cache is exactly that
 * - `timestamp` and `count` attributes included - in `%APPDATA%/StormFront/<list>/cmdlist1.xml`, and a
 * file that can be diffed against that one is worth more than a tidier format. It is written and read
 * here rather than by the protocol parser, so the escaping only has to agree with itself.
 */
class CommandListStore(
    private val baseDir: String,
) {
    private val logger = Logger.withTag("CommandListStore")

    data class CachedList(
        val serial: String,
        val commands: List<CmdDefinition>,
    )

    /**
     * Reads the list cached for [listName], or null when there is none, it cannot be read, or it has
     * no serial - all of which just mean asking the server for the list from scratch.
     */
    fun load(listName: String): CachedList? {
        val path = pathFor(listName)
        return try {
            if (!SystemFileSystem.exists(path)) return null
            var serial: String? = null
            val commands = mutableListOf<CmdDefinition>()
            SystemFileSystem.source(path).buffered().use { source ->
                while (true) {
                    val line = source.readLine() ?: break
                    if (serial == null) {
                        serial = line.attribute("timestamp")
                    }
                    parseCli(line)?.let { commands.add(it) }
                }
            }
            serial?.let { CachedList(it, commands) }
        } catch (e: Exception) {
            // A cache that cannot be read is only a slower login, so log it and move on.
            logger.w(e) { "Could not read the cached command list for $listName" }
            null
        }
    }

    /** Writes [commands] and the [serial] the server gave them, replacing whatever was there. */
    fun save(
        listName: String,
        serial: String,
        commands: Collection<CmdDefinition>,
    ) {
        try {
            SystemFileSystem.createDirectories(Path(baseDir, DIRECTORY))
            val path = pathFor(listName)
            SystemFileSystem.sink(path).buffered().use { sink ->
                sink.writeString("<cmdlist timestamp=\"${serial.escaped()}\" count=\"${commands.size}\">\n")
                commands.forEach { cmd ->
                    sink.writeString(
                        "<cli coord=\"${cmd.coord.escaped()}\"" +
                            " menu=\"${cmd.menu.escaped()}\"" +
                            " command=\"${cmd.command.escaped()}\"" +
                            " menu_cat=\"${cmd.category.escaped()}\"/>\n",
                    )
                }
                sink.writeString("</cmdlist>\n")
            }
        } catch (e: Exception) {
            // Same again: failing to cache costs a re-send next time, nothing more.
            logger.w(e) { "Could not write the cached command list for $listName" }
        }
    }

    private fun pathFor(listName: String) = Path(baseDir, DIRECTORY, "${listName.fileNameSafe()}.xml")

    companion object {
        private const val DIRECTORY = "cmdlists"

        // The list name comes from the server, so it does not get to name a file on its own terms.
        private fun String.fileNameSafe() = map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("")

        private fun String.escaped() =
            replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")

        private fun String.unescaped() =
            replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")

        private fun String.attribute(name: String): String? {
            val start = indexOf("$name=\"").takeIf { it >= 0 }?.plus(name.length + 2) ?: return null
            val end = indexOf('"', start).takeIf { it >= 0 } ?: return null
            return substring(start, end).unescaped()
        }

        private fun parseCli(line: String): CmdDefinition? {
            if (!line.startsWith("<cli ")) return null
            val coord = line.attribute("coord") ?: return null
            val command = line.attribute("command") ?: return null
            val menu = line.attribute("menu") ?: return null
            val category = line.attribute("menu_cat") ?: return null
            return CmdDefinition(coord = coord, command = command, menu = menu, category = category)
        }
    }
}
