package warlockfe.warlock3.core.mudmobile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire models for the MUD Mobile REST API (https://mudmobile.com). See
// docs/specs/mudmobile-integration.md for the contract these mirror.

/** A character profile the user saved in MUD Mobile, returned by `GET /api/characters`. */
@Serializable
data class MudMobileCharacter(
    val id: String,
    val account: String,
    val game: String,
    val characterCode: String,
    val characterName: String,
    val lastUsedAt: String? = null,
)

@Serializable
internal data class CharactersResponse(
    val characters: List<MudMobileCharacter> = emptyList(),
)

@Serializable
internal data class CreateCharacterRequest(
    val account: String,
    val game: String,
    val characterCode: String,
    val characterName: String,
)

@Serializable
internal data class CharacterResponse(
    val character: MudMobileCharacter,
)

@Serializable
internal data class CreateSessionRequest(
    val game: String,
    val character: String,
    val gamehost: String,
    val gameport: Int,
    val keyHash: String,
)

/** Where Warlock should open its game socket; substitutes for the real gamehost/gameport. */
@Serializable
data class SessionConnectInfo(
    val host: String,
    val port: Int,
    val tls: Boolean,
)

@Serializable
internal data class CreateSessionResponse(
    val sessionId: String,
    val connect: SessionConnectInfo,
)

@Serializable
internal data class SessionStatusBody(
    val id: String? = null,
    val status: String? = null,
    val statusDetail: String? = null,
    val ready: Boolean = false,
)

/** Common error envelope: `{ "error": "<code>", "detail"?: ..., "limit"?, "active"? }`. */
@Serializable
internal data class MudMobileErrorBody(
    val error: String? = null,
    @SerialName("limit") val limit: Int? = null,
    @SerialName("active") val active: Int? = null,
)

/** Result of `GET /api/characters`. */
sealed interface CharactersResult {
    data class Success(
        val characters: List<MudMobileCharacter>,
    ) : CharactersResult

    data object Unauthorized : CharactersResult

    data class Error(
        val message: String,
    ) : CharactersResult
}

/** Result of `POST /api/characters`. */
sealed interface CreateCharacterResult {
    data class Success(
        val character: MudMobileCharacter,
    ) : CreateCharacterResult

    data object Unauthorized : CreateCharacterResult

    data class Error(
        val message: String,
    ) : CreateCharacterResult
}

/** Result of `POST /api/sessions`. */
sealed interface CreateSessionResult {
    data class Success(
        val sessionId: String,
        val connect: SessionConnectInfo,
    ) : CreateSessionResult

    data object Unauthorized : CreateSessionResult

    data object SubscriptionRequired : CreateSessionResult

    data class ConcurrentLimitReached(
        val limit: Int?,
        val active: Int?,
    ) : CreateSessionResult

    data class Error(
        val message: String,
    ) : CreateSessionResult
}

/** Result of `GET /api/sessions/{id}`. */
sealed interface SessionStatusResult {
    data class Success(
        val status: String?,
        val statusDetail: String?,
        val ready: Boolean,
    ) : SessionStatusResult

    data object Unauthorized : SessionStatusResult

    data object NotFound : SessionStatusResult

    data class Error(
        val message: String,
    ) : SessionStatusResult
}
