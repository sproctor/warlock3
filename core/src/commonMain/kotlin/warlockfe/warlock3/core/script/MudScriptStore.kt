package warlockfe.warlock3.core.script

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
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
 * Only `http` and `https` URLs are fetched, and a URL that names a Mudlet package (`.mpackage`,
 * `.zip`, `.xml`, `.trigger`) is passed over: a game that sends every client the same
 * `Client.GUI` message is offering Mudlet's interface, which is not Lua we can run.
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
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return MudScriptLoad.Failed("The MUD's script could not be fetched: only http and https URLs are supported, not $url")
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
        val dir = characterConfigStore.directoryFor(characterId)
        val scriptPath = Path(dir, SCRIPT_FILE)
        val versionPath = Path(dir, VERSION_FILE)
        if (!fileSystem.exists(scriptPath) || !fileSystem.exists(versionPath)) return null
        return try {
            Cached(
                version =
                    fileSystem
                        .source(versionPath)
                        .buffered()
                        .use { it.readString() }
                        .trim(),
                script = fileSystem.source(scriptPath).buffered().use { it.readString() },
            )
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
        try {
            fileSystem.createDirectories(dir)
            fileSystem.sink(Path(dir, SCRIPT_FILE)).buffered().use { it.writeString(script) }
            fileSystem.sink(Path(dir, VERSION_FILE)).buffered().use { it.writeString(version) }
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
        const val VERSION_FILE = "mud-script.version"
        private val MUDLET_EXTENSIONS = listOf(".mpackage", ".zip", ".xml", ".trigger")
    }
}

/** Fetches a MUD's script over HTTP, refusing anything larger than a script has any business being. */
class HttpMudScriptFetcher(
    private val httpClient: HttpClient,
) : MudScriptFetcher {
    override suspend fun fetch(url: String): String {
        val response = httpClient.get(url)
        if (!response.status.isSuccess()) throw IOException("HTTP ${response.status}")
        val length = response.contentLength()
        if (length != null && length > MAX_BYTES) throw IOException("the script is $length bytes, more than the $MAX_BYTES allowed")
        val bytes = response.bodyAsBytes()
        if (bytes.size > MAX_BYTES) throw IOException("the script is ${bytes.size} bytes, more than the $MAX_BYTES allowed")
        return bytes.decodeToString()
    }

    companion object {
        const val MAX_BYTES = 1024 * 1024
    }
}
