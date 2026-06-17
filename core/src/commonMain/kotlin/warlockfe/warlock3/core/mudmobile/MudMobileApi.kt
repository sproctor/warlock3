package warlockfe.warlock3.core.mudmobile

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
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
) {
    private val logger = Logger.withTag("MudMobileApi")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getCharacters(token: String): CharactersResult =
        runCatching {
            val response =
                httpClient.get("$baseUrl/api/characters") {
                    header(HttpHeaders.Authorization, "Bearer $token")
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
        }.getOrElse { e ->
            logger.e(e) { "getCharacters failed" }
            CharactersResult.Error(e.message ?: "Network error")
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
        runCatching {
            val requestBody =
                json.encodeToString(
                    CreateCharacterRequest(
                        account = account,
                        game = game,
                        characterCode = characterCode,
                        characterName = characterName,
                    ),
                )
            val response =
                httpClient.post("$baseUrl/api/characters") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
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
        }.getOrElse { e ->
            logger.e(e) { "createCharacter failed" }
            CreateCharacterResult.Error(e.message ?: "Network error")
        }

    /** Remove a saved character. Returns true on a 2xx (or 404, already gone). */
    suspend fun deleteCharacter(
        token: String,
        id: String,
    ): Boolean =
        runCatching {
            val response =
                httpClient.delete("$baseUrl/api/characters/$id") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            response.status.value in 200..299 || response.status.value == 404
        }.getOrElse { e ->
            logger.d(e) { "deleteCharacter failed" }
            false
        }

    suspend fun createSession(
        token: String,
        game: String,
        character: String,
        gamehost: String,
        gameport: Int,
        keyHash: String,
    ): CreateSessionResult =
        runCatching {
            val requestBody =
                json.encodeToString(
                    CreateSessionRequest(
                        game = game,
                        character = character,
                        gamehost = gamehost,
                        gameport = gameport,
                        keyHash = keyHash,
                    ),
                )
            val response =
                httpClient.post("$baseUrl/api/sessions") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
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
        }.getOrElse { e ->
            logger.e(e) { "createSession failed" }
            CreateSessionResult.Error(e.message ?: "Network error")
        }

    /** Poll a session's status (informational; used to wait for readiness). */
    suspend fun getSession(
        token: String,
        sessionId: String,
    ): SessionStatusResult =
        runCatching {
            val response =
                httpClient.get("$baseUrl/api/sessions/$sessionId") {
                    header(HttpHeaders.Authorization, "Bearer $token")
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
        }.getOrElse { e ->
            logger.d(e) { "getSession failed" }
            SessionStatusResult.Error(e.message ?: "Network error")
        }

    /** Best-effort cleanup. Returns true on a 2xx. A 404 (already gone) is treated as success. */
    suspend fun deleteSession(
        token: String,
        sessionId: String,
    ): Boolean =
        runCatching {
            val response =
                httpClient.delete("$baseUrl/api/sessions/$sessionId") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            response.status.value in 200..299 || response.status.value == 404
        }.getOrElse { e ->
            logger.d(e) { "deleteSession failed" }
            false
        }

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
