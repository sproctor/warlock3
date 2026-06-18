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

// --- Warlock settings sync (`/api/warlock/files`) ------------------------------------------------
// Back up / restore / sync Warlock's own `.toml` settings. Every file is identified by
// `hash = sha256_hex(raw_file_bytes)`; writes are compare-and-swap against `baseHash`. See §4.5 of
// docs/specs/mudmobile-integration.md.

/** Metadata for one stored settings file, from `GET /api/warlock/files`. */
@Serializable
data class WarlockFileMeta(
    val path: String,
    val size: Long = 0,
    val modified: String? = null,
    val hash: String,
)

@Serializable
internal data class WarlockFilesResponse(
    val files: List<WarlockFileMeta> = emptyList(),
)

/** Body of `GET /api/warlock/files/file?path=…`. */
@Serializable
internal data class WarlockFileBody(
    val path: String,
    val content: String,
    val hash: String,
    val modified: String? = null,
)

@Serializable
internal data class WriteWarlockFileRequest(
    val path: String,
    val content: String,
    val baseHash: String? = null,
    val overwrite: Boolean = false,
)

@Serializable
internal data class WriteWarlockFileResponse(
    val ok: Boolean = false,
    val path: String? = null,
    val hash: String? = null,
    val modified: String? = null,
)

/** 409 conflict envelope for a write: `{ "error": "conflict", "currentHash": …, "modified": … }`. */
@Serializable
internal data class WarlockFileConflictBody(
    val error: String? = null,
    val currentHash: String? = null,
    val modified: String? = null,
)

/** Result of `GET /api/warlock/files`. */
sealed interface ListWarlockFilesResult {
    data class Success(
        val files: List<WarlockFileMeta>,
    ) : ListWarlockFilesResult

    data object Unauthorized : ListWarlockFilesResult

    data class Error(
        val message: String,
    ) : ListWarlockFilesResult
}

/** Result of `GET /api/warlock/files/file?path=…`. */
sealed interface ReadWarlockFileResult {
    data class Success(
        val content: String,
        val hash: String,
        val modified: String?,
    ) : ReadWarlockFileResult

    data object NotFound : ReadWarlockFileResult

    data object Unauthorized : ReadWarlockFileResult

    data class Error(
        val message: String,
    ) : ReadWarlockFileResult
}

/** Result of `POST /api/warlock/files` (compare-and-swap write). */
sealed interface WriteWarlockFileResult {
    data class Success(
        val hash: String,
        val modified: String?,
    ) : WriteWarlockFileResult

    /** The remote moved on since [baseHash]; nothing was written. */
    data class Conflict(
        val currentHash: String?,
        val modified: String?,
    ) : WriteWarlockFileResult

    data object Unauthorized : WriteWarlockFileResult

    data class Error(
        val message: String,
    ) : WriteWarlockFileResult
}
