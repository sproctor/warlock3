package warlockfe.warlock3.core.script

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Url
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString
import warlockfe.warlock3.core.client.MudScriptOffer
import warlockfe.warlock3.core.prefs.config.CharacterConfigStore

/** What came of a MUD's offer of a script: the Lua to run, a reason not to, or what went wrong. */
sealed interface MudScriptLoad {
    data class Loaded(
        val script: String,
    ) : MudScriptLoad

    data class Skipped(
        val reason: String,
    ) : MudScriptLoad

    data class Failed(
        val message: String,
    ) : MudScriptLoad
}

/** Downloads a MUD's script. Separate from the store so the store can be tested without a server. */
fun interface MudScriptFetcher {
    /** The text at [url], or throws with a message worth showing the user. */
    suspend fun fetch(url: String): String
}

/**
 * Turns a MUD's offer of a script ([MudScriptOffer]) into the Lua to run, the way Mudlet keeps a
 * game's interface package: the script is downloaded once per version and kept beside the
 * character's settings, so a later login with the same version offered runs the copy on disk and
 * a new version fetches afresh. A script sent inline needs no fetching and is not kept.
 *
 * The copy on disk is one file, the version in a comment on its first line, replaced in one move
 * so that a script is never on disk under another version's number.
 *
 * What is fetched is code the MUD chose, so it is fetched only over `https`, and never from the
 * network the user's machine is on: a URL whose host is a private, link-local or otherwise
 * reserved address is refused (see [unsafeHostReason]). The user's own machine is the exception,
 * for testing a MUD and its script locally: a loopback host is fetched from, over plain `http`
 * too. A URL that names a Mudlet package
 * (`.mpackage`, `.zip`, `.xml`, `.trigger`) is passed over: a game that sends every client the
 * same `Client.GUI` message is offering Mudlet's interface, which is not Lua we can run.
 */
class MudScriptStore(
    private val characterConfigStore: CharacterConfigStore,
    private val fileSystem: FileSystem,
    private val fetcher: MudScriptFetcher,
) {
    private val logger = Logger.withTag("MudScriptStore")

    suspend fun load(
        characterId: String,
        offer: MudScriptOffer,
    ): MudScriptLoad {
        offer.script?.let { return MudScriptLoad.Loaded(it) }
        val url = offer.url ?: return MudScriptLoad.Skipped("The MUD offered a script with neither a URL nor a script in it.")
        val fileName = url.substringAfterLast('/').substringBefore('?')
        if (MUDLET_EXTENSIONS.any { fileName.endsWith(it, ignoreCase = true) }) {
            return MudScriptLoad.Skipped("The MUD offered a Mudlet package ($fileName), which is not a script we can run.")
        }
        unsafeUrlReason(url)?.let { reason ->
            return MudScriptLoad.Failed("The MUD's script was not fetched from $url: $reason")
        }
        cached(characterId)?.takeIf { it.version == offer.version }?.let {
            logger.d { "Using the cached version ${offer.version} of the script for $characterId" }
            return MudScriptLoad.Loaded(it.script)
        }
        val script =
            try {
                fetcher.fetch(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(e) { "Could not fetch the MUD's script from $url" }
                return MudScriptLoad.Failed("The MUD's script could not be fetched from $url: ${e.message ?: e::class.simpleName}")
            }
        save(characterId, offer.version, script)
        return MudScriptLoad.Loaded(script)
    }

    /** The script kept on disk for the character, with the version it was fetched as. */
    fun cached(characterId: String): Cached? {
        val path = Path(characterConfigStore.directoryFor(characterId), SCRIPT_FILE)
        if (!fileSystem.exists(path)) return null
        return try {
            val text = fileSystem.source(path).buffered().use { it.readString() }
            val header = text.substringBefore('\n')
            if (!header.startsWith(VERSION_HEADER)) {
                logger.w { "The cached script for $characterId has no version line; ignoring it" }
                return null
            }
            Cached(version = header.removePrefix(VERSION_HEADER).trim(), script = text.substringAfter('\n', ""))
        } catch (e: IOException) {
            logger.w(e) { "Could not read the cached script for $characterId" }
            null
        }
    }

    private fun save(
        characterId: String,
        version: String,
        script: String,
    ) {
        val dir = characterConfigStore.directoryFor(characterId)
        val path = Path(dir, SCRIPT_FILE)
        val staging = Path(dir, "$SCRIPT_FILE.tmp")
        try {
            fileSystem.createDirectories(dir)
            fileSystem.sink(staging).buffered().use {
                // The version on one line, so it and the script are one record.
                it.writeString(VERSION_HEADER + version.replace('\n', ' ').replace('\r', ' ') + "\n")
                it.writeString(script)
            }
            fileSystem.atomicMove(staging, path)
        } catch (e: IOException) {
            // The script still runs this session; it is fetched again next time.
            logger.w(e) { "Could not keep the MUD's script for $characterId" }
        }
    }

    data class Cached(
        val version: String,
        val script: String,
    )

    companion object {
        const val SCRIPT_FILE = "mud-script.lua"
        const val VERSION_HEADER = "-- Warlock: the script the MUD sent, version "
        private val MUDLET_EXTENSIONS = listOf(".mpackage", ".zip", ".xml", ".trigger")
        private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

        /** Why [url] must not be fetched, or null when it may be. */
        fun unsafeUrlReason(url: String): String? {
            // Ktor's parser takes anything, reading "not a url" as a path on localhost; a URL
            // says its scheme.
            if (!SCHEME.containsMatchIn(url)) return "that is not a URL"
            val parsed = runCatching { Url(url) }.getOrNull() ?: return "that is not a URL"
            val host = hostName(parsed.host)
            if (host.isEmpty()) return "the URL has no host"
            val scheme = parsed.protocol.name.lowercase()
            if (scheme != "https" && scheme != "http") return "only https URLs are fetched, since what is fetched is run"
            // This machine is the developer's: a MUD and its script under test, served locally.
            if (isLoopback(host)) return null
            if (scheme != "https") return "only https URLs are fetched, since what is fetched is run"
            return unsafeHostReason(host)
        }

        /** Whether [host] is this machine: `localhost`, a name under it, or a loopback address. */
        fun isLoopback(host: String): Boolean {
            val name = hostName(host)
            if (name == "localhost" || name.endsWith(".localhost")) return true
            ipv4Octets(name)?.let { return it[0] == 127 }
            if (':' in name) {
                val mapped = name.substringAfterLast(':')
                if ('.' in mapped) return ipv4Octets(mapped)?.let { it[0] == 127 } == true
                return name == "::1"
            }
            return false
        }

        /**
         * Why a fetch must not go to [host], or null when it may: a script is fetched from the
         * MUD's web site, never from the network this machine is on. Only what the host name
         * itself says is checked - a name that resolves to a private address is not caught.
         */
        fun unsafeHostReason(host: String): String? {
            val name = hostName(host)
            if (name.isEmpty()) return "the URL has no host"
            ipv4Octets(name)?.let { return unsafeIpv4Reason(it) }
            if (':' in name) return unsafeIpv6Reason(name)
            // A bare number, decimal or hex, is an IPv4 address in disguise (2130706433 is 127.0.0.1).
            if (name.all { it.isDigit() } || name.startsWith("0x")) return "the host is a numeric address"
            return null
        }

        private fun hostName(host: String): String =
            host
                .trim()
                .removePrefix("[")
                .removeSuffix("]")
                .lowercase()

        private fun ipv4Octets(name: String): List<Int>? {
            val parts = name.split('.')
            if (parts.size != 4) return null
            val octets = parts.map { part -> part.toIntOrNull()?.takeIf { it in 0..255 && part.length <= 3 } ?: return null }
            return octets
        }

        private fun unsafeIpv4Reason(o: List<Int>): String? =
            when {
                o[0] == 127 -> "the address is a loopback address"
                o[0] == 0 -> "the address is reserved"
                o[0] == 10 || (o[0] == 172 && o[1] in 16..31) || (o[0] == 192 && o[1] == 168) -> "the address is a private address"
                o[0] == 100 && o[1] in 64..127 -> "the address is a private address"
                o[0] == 169 && o[1] == 254 -> "the address is a link-local address"
                o[0] == 192 && o[1] == 0 && o[2] == 0 -> "the address is reserved"
                o[0] == 198 && o[1] in 18..19 -> "the address is reserved"
                o[0] >= 224 -> "the address is reserved"
                else -> null
            }

        private fun unsafeIpv6Reason(name: String): String? {
            // An IPv4 address carried in IPv6, ::ffff:127.0.0.1, is judged as the IPv4 address.
            val mapped = name.substringAfterLast(':')
            if ('.' in mapped) {
                return ipv4Octets(mapped)?.let { unsafeIpv4Reason(it) } ?: "the address is malformed"
            }
            return when {
                name == "::1" -> {
                    "the address is a loopback address"
                }

                name == "::" -> {
                    "the address is reserved"
                }

                name.startsWith("fc") || name.startsWith("fd") -> {
                    "the address is a private address"
                }

                name.startsWith("fe8") || name.startsWith("fe9") || name.startsWith("fea") || name.startsWith("feb") -> {
                    "the address is a link-local address"
                }

                else -> {
                    null
                }
            }
        }
    }
}

/**
 * Fetches a MUD's script over HTTPS, refusing anything larger than a script has any business
 * being, and not following redirects: the [httpClient] is made with redirects off, so that the
 * host checked by the store is the host connected to.
 */
class HttpMudScriptFetcher(
    private val httpClient: HttpClient,
) : MudScriptFetcher {
    override suspend fun fetch(url: String): String =
        httpClient.prepareGet(url).execute { response ->
            if (response.status.value in 300..399) throw IOException("HTTP ${response.status} (redirects are not followed)")
            if (!response.status.isSuccess()) throw IOException("HTTP ${response.status}")
            val length = response.contentLength()
            if (length != null && length > MAX_BYTES) throw IOException("the script is $length bytes, more than the $MAX_BYTES allowed")
            response.bodyAsChannel().readAtMost(MAX_BYTES).decodeToString()
        }

    companion object {
        const val MAX_BYTES = 1024 * 1024

        /** Reads the whole channel, or throws as soon as it has read more than [max] bytes. */
        suspend fun ByteReadChannel.readAtMost(max: Int): ByteArray {
            val chunk = ByteArray(8192)
            val out = Buffer()
            var total = 0L
            while (true) {
                val n = readAvailable(chunk, 0, chunk.size)
                if (n < 0) break
                total += n
                if (total > max) throw IOException("the script is more than the $max bytes allowed")
                out.write(chunk, 0, n)
            }
            return out.readByteArray()
        }
    }
}
