package warlockfe.warlock3.core.mudmobile

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json

/**
 * Thin client for the MUD Mobile REST API. Every request authenticates with the user's device
 * token (`wlk_…`) via `Authorization: Bearer`. The raw game key and play.net password never pass
 * through here; only `keyHash = sha256(key)` is ever sent (see [createSession]).
 *
 * The contract is documented in docs/specs/mudmobile-integration.md.
 */
class MudMobileApi(
    private val httpClient: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : WarlockFilesApi {
    private val logger = Logger.withTag("MudMobileApi")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getCharacters(token: String): CharactersResult =
        request("getCharacters", networkError = { CharactersResult.Error(it) }) {
            val response =
                httpClient.get("$baseUrl/api/characters") {
                    bearer(token)
                }
            when (response.status.value) {
                in 200..299 -> {
                    val body = json.decodeFromString<CharactersResponse>(response.bodyAsText())
                    CharactersResult.Success(body.characters)
                }

                401 -> {
                    CharactersResult.Unauthorized
                }

                else -> {
                    CharactersResult.Error(errorMessage(response))
                }
            }
        }

    /**
     * Register (idempotent upsert) a character discovered via a local SGE login so it shows up in
     * [getCharacters]. No secrets are sent — never the password or game key. Safe to call on every
     * successful launch; the server keys on (user, account, game, characterCode).
     */
    suspend fun createCharacter(
        token: String,
        account: String,
        game: String,
        characterCode: String,
        characterName: String,
    ): CreateCharacterResult =
        request("createCharacter", networkError = { CreateCharacterResult.Error(it) }) {
            val response =
                httpClient.post("$baseUrl/api/characters") {
                    bearer(token)
                    contentType(ContentType.Application.Json)
                    setBody(
                        json.encodeToString(
                            CreateCharacterRequest(
                                account = account,
                                game = game,
                                characterCode = characterCode,
                                characterName = characterName,
                            ),
                        ),
                    )
                }
            when (response.status.value) {
                in 200..299 -> {
                    val body = json.decodeFromString<CharacterResponse>(response.bodyAsText())
                    CreateCharacterResult.Success(body.character)
                }

                401 -> {
                    CreateCharacterResult.Unauthorized
                }

                else -> {
                    CreateCharacterResult.Error(errorMessage(response))
                }
            }
        }

    /** Remove a saved character. Returns true on a 2xx (or 404, already gone). */
    suspend fun deleteCharacter(
        token: String,
        id: String,
    ): Boolean =
        request("deleteCharacter", logAtError = false, networkError = { false }) {
            httpClient
                .delete("$baseUrl/api/characters/$id") { bearer(token) }
                .isSuccessOrGone()
        }

    suspend fun createSession(
        token: String,
        game: String,
        character: String,
        gamehost: String,
        gameport: Int,
        keyHash: String,
    ): CreateSessionResult =
        request("createSession", networkError = { CreateSessionResult.Error(it) }) {
            val response =
                httpClient.post("$baseUrl/api/sessions") {
                    bearer(token)
                    contentType(ContentType.Application.Json)
                    setBody(
                        json.encodeToString(
                            CreateSessionRequest(
                                game = game,
                                character = character,
                                gamehost = gamehost,
                                gameport = gameport,
                                keyHash = keyHash,
                            ),
                        ),
                    )
                }
            when (response.status.value) {
                in 200..299 -> {
                    val body = json.decodeFromString<CreateSessionResponse>(response.bodyAsText())
                    CreateSessionResult.Success(body.sessionId, body.connect)
                }

                401 -> {
                    CreateSessionResult.Unauthorized
                }

                402 -> {
                    CreateSessionResult.SubscriptionRequired
                }

                409 -> {
                    val body = parseError(response)
                    CreateSessionResult.ConcurrentLimitReached(body?.limit, body?.active)
                }

                else -> {
                    CreateSessionResult.Error(errorMessage(response))
                }
            }
        }

    /** Poll a session's status (informational; used to wait for readiness). */
    suspend fun getSession(
        token: String,
        sessionId: String,
    ): SessionStatusResult =
        request("getSession", logAtError = false, networkError = { SessionStatusResult.Error(it) }) {
            val response =
                httpClient.get("$baseUrl/api/sessions/$sessionId") {
                    bearer(token)
                }
            when (response.status.value) {
                in 200..299 -> {
                    val body = json.decodeFromString<SessionStatusBody>(response.bodyAsText())
                    SessionStatusResult.Success(body.status, body.statusDetail, body.ready)
                }

                401 -> {
                    SessionStatusResult.Unauthorized
                }

                404 -> {
                    SessionStatusResult.NotFound
                }

                else -> {
                    SessionStatusResult.Error(errorMessage(response))
                }
            }
        }

    /** Best-effort cleanup. Returns true on a 2xx. A 404 (already gone) is treated as success. */
    suspend fun deleteSession(
        token: String,
        sessionId: String,
    ): Boolean =
        request("deleteSession", logAtError = false, networkError = { false }) {
            httpClient
                .delete("$baseUrl/api/sessions/$sessionId") { bearer(token) }
                .isSuccessOrGone()
        }

    // --- Warlock settings sync (`/api/warlock/files`) -------------------------------------------

    /** List the user's stored settings files (path + content hash + modified time). */
    override suspend fun listWarlockFiles(token: String): ListWarlockFilesResult =
        request("listWarlockFiles", networkError = { ListWarlockFilesResult.Error(it) }) {
            val response =
                httpClient.get("$baseUrl/api/warlock/files") {
                    bearer(token)
                }
            when (response.status.value) {
                in 200..299 -> {
                    val body = json.decodeFromString<WarlockFilesResponse>(response.bodyAsText())
                    ListWarlockFilesResult.Success(body.files)
                }

                401 -> {
                    ListWarlockFilesResult.Unauthorized
                }

                else -> {
                    ListWarlockFilesResult.Error(errorMessage(response))
                }
            }
        }

    /** Read one settings file's content + hash. */
    override suspend fun readWarlockFile(
        token: String,
        path: String,
    ): ReadWarlockFileResult =
        request("readWarlockFile", networkError = { ReadWarlockFileResult.Error(it) }) {
            val response =
                httpClient.get("$baseUrl/api/warlock/files/file") {
                    bearer(token)
                    parameter("path", path)
                }
            when (response.status.value) {
                in 200..299 -> {
                    val body = json.decodeFromString<WarlockFileBody>(response.bodyAsText())
                    ReadWarlockFileResult.Success(body.content, body.hash, body.modified)
                }

                401 -> {
                    ReadWarlockFileResult.Unauthorized
                }

                404 -> {
                    ReadWarlockFileResult.NotFound
                }

                else -> {
                    ReadWarlockFileResult.Error(errorMessage(response))
                }
            }
        }

    /**
     * Compare-and-swap write of one settings file. [baseHash] = the hash you last saw (null =
     * create-only). [overwrite] = force, ignoring [baseHash]. A `409` returns [WriteWarlockFileResult.Conflict]
     * with the current remote hash so the caller can diff/resolve.
     */
    override suspend fun writeWarlockFile(
        token: String,
        path: String,
        content: String,
        baseHash: String?,
        overwrite: Boolean,
    ): WriteWarlockFileResult =
        request("writeWarlockFile", networkError = { WriteWarlockFileResult.Error(it) }) {
            val response =
                httpClient.post("$baseUrl/api/warlock/files") {
                    bearer(token)
                    contentType(ContentType.Application.Json)
                    setBody(
                        json.encodeToString(
                            WriteWarlockFileRequest(
                                path = path,
                                content = content,
                                baseHash = baseHash,
                                overwrite = overwrite,
                            ),
                        ),
                    )
                }
            when (response.status.value) {
                in 200..299 -> {
                    val body = json.decodeFromString<WriteWarlockFileResponse>(response.bodyAsText())
                    val hash = body.hash
                    if (hash != null) {
                        WriteWarlockFileResult.Success(hash, body.modified)
                    } else {
                        WriteWarlockFileResult.Error("Write succeeded but no hash was returned")
                    }
                }

                401 -> {
                    WriteWarlockFileResult.Unauthorized
                }

                409 -> {
                    val body =
                        runCatching { json.decodeFromString<WarlockFileConflictBody>(response.bodyAsText()) }.getOrNull()
                    WriteWarlockFileResult.Conflict(body?.currentHash, body?.modified)
                }

                else -> {
                    WriteWarlockFileResult.Error(errorMessage(response))
                }
            }
        }

    /** Delete one settings file. Returns true on a 2xx (or 404, already gone). */
    override suspend fun deleteWarlockFile(
        token: String,
        path: String,
    ): Boolean =
        request("deleteWarlockFile", logAtError = false, networkError = { false }) {
            httpClient
                .delete("$baseUrl/api/warlock/files/file") {
                    bearer(token)
                    parameter("path", path)
                }.isSuccessOrGone()
        }

    /**
     * Run an API call, mapping any thrown exception to a network-error result of type [T]. Inline so
     * the suspending [block] runs in the caller's coroutine. [logAtError] picks the log level (the
     * informational best-effort calls log at debug).
     */
    private inline fun <T> request(
        logLabel: String,
        logAtError: Boolean = true,
        networkError: (message: String) -> T,
        block: () -> T,
    ): T =
        runCatching { block() }.getOrElse { e ->
            if (logAtError) {
                logger.e(e) { "$logLabel failed" }
            } else {
                logger.d(e) { "$logLabel failed" }
            }
            networkError(e.message ?: "Network error")
        }

    private fun HttpRequestBuilder.bearer(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private fun HttpResponse.isSuccessOrGone(): Boolean = status.value in 200..299 || status.value == 404

    private suspend fun parseError(response: HttpResponse): MudMobileErrorBody? =
        runCatching { json.decodeFromString<MudMobileErrorBody>(response.bodyAsText()) }.getOrNull()

    private suspend fun errorMessage(response: HttpResponse): String {
        val code = parseError(response)?.error
        return if (code != null) {
            "$code (HTTP ${response.status.value})"
        } else {
            "HTTP ${response.status.value}"
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://mudmobile.com"
    }
}
