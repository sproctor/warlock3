package warlockfe.warlock3.core.mudmobile

/**
 * The subset of the MUD Mobile API used by [WarlockSettingsSync] (the `/api/warlock/files`
 * endpoints). Extracted so the sync engine can be unit-tested against an in-memory fake without a
 * real HTTP client. [MudMobileApi] is the production implementation.
 */
interface WarlockFilesApi {
    suspend fun listWarlockFiles(token: String): ListWarlockFilesResult

    suspend fun readWarlockFile(
        token: String,
        path: String,
    ): ReadWarlockFileResult

    suspend fun writeWarlockFile(
        token: String,
        path: String,
        content: String,
        baseHash: String?,
        overwrite: Boolean = false,
    ): WriteWarlockFileResult

    suspend fun deleteWarlockFile(
        token: String,
        path: String,
    ): Boolean
}
