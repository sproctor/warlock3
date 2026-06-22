package warlockfe.warlock3.core.prefs.repositories

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.CompassStyle
import warlockfe.warlock3.core.prefs.ReleaseChannelSetting
import warlockfe.warlock3.core.prefs.ThemeSetting
import warlockfe.warlock3.core.prefs.config.ClientConfigStore
import warlockfe.warlock3.core.prefs.dao.ClientSettingDao
import warlockfe.warlock3.core.prefs.models.ClientSettingEntity
import warlockfe.warlock3.core.util.LogSettings
import warlockfe.warlock3.core.util.LogType
import warlockfe.warlock3.core.util.WarlockDirs

/**
 * Client-wide settings. The user-editable application settings (theme, scrollback, link/image
 * toggles, logging, skin, release channel) live in `client.toml` via [ClientConfigStore]; the
 * machine-managed bits, the main window's last size ([getWidth]/[getHeight]), the last logged-in
 * username, and the update-skip flag, stay in SQLite where high-churn geometry belongs.
 */
class ClientSettingRepository(
    private val clientSettingDao: ClientSettingDao,
    private val clientConfigStore: ClientConfigStore,
    private val warlockDirs: WarlockDirs,
) {
    // --- Machine state / geometry: SQLite ---

    suspend fun getWidth(): Int? = getInt("width")

    suspend fun getHeight(): Int? = getInt("height")

    suspend fun getIgnoreUpdates(): Boolean = getBoolean("ignoreUpdates") ?: false

    fun observeIgnoreUpdates(): Flow<Boolean> = observe("ignoreUpdates").map { it?.toBoolean() ?: false }

    suspend fun getLastUsername(): String? = get("lastUsername")

    /** The id of the most recently launched connection, used for auto-connect-on-startup. */
    suspend fun getLastConnectionId(): String? = get("lastConnectionId")

    suspend fun putLastConnectionId(value: String?) {
        put("lastConnectionId", value)
    }

    suspend fun putWidth(value: Int) {
        putInt("width", value)
    }

    suspend fun putHeight(value: Int) {
        putInt("height", value)
    }

    suspend fun putLastUsername(value: String?) {
        put("lastUsername", value)
    }

    suspend fun putIgnoreUpdates(value: Boolean) {
        putBoolean("ignoreUpdates", value)
    }

    // --- User-editable application settings: client.toml ---

    fun observeTheme(): Flow<ThemeSetting> =
        clientConfigStore.observeClient().map { config ->
            config.theme?.let { runCatching { ThemeSetting.valueOf(it) }.getOrNull() } ?: ThemeSetting.AUTO
        }

    fun observeCompassStyle(): Flow<CompassStyle> =
        clientConfigStore.observeClient().map { config ->
            config.compassStyle?.let { runCatching { CompassStyle.valueOf(it) }.getOrNull() } ?: CompassStyle.BUTTONS
        }

    suspend fun getReleaseChannel(): ReleaseChannelSetting =
        clientConfigStore
            .currentClient()
            .releaseChannel
            ?.let { value -> ReleaseChannelSetting.entries.firstOrNull { it.name == value } }
            ?: ReleaseChannelSetting.CURRENT

    fun observeReleaseChannel(): Flow<ReleaseChannelSetting> =
        clientConfigStore.observeClient().map { config ->
            config.releaseChannel?.let { value -> ReleaseChannelSetting.entries.firstOrNull { it.name == value } }
                ?: ReleaseChannelSetting.CURRENT
        }

    fun observeSkinFile(): Flow<String?> = clientConfigStore.observeClient().map { it.skinFile }

    fun observeLogSettings(): Flow<LogSettings> =
        clientConfigStore.observeClient().map { config ->
            LogSettings(
                basePath = config.logPath ?: warlockDirs.logDir,
                type = config.logType?.let { runCatching { LogType.valueOf(it) }.getOrNull() } ?: LogType.SIMPLE,
                logTimestamps = config.logTimestamps,
            )
        }

    fun observeMaxScrollLines(): Flow<Int> = clientConfigStore.observeClient().map { it.scrollback ?: DEFAULT_MAX_SCROLL_LINES }

    fun observeMinCommandLength(): Flow<Int> = clientConfigStore.observeClient().map { it.minCommandLength ?: DEFAULT_MIN_COMMAND_LENGTH }

    /**
     * Maximum number of commands retained in the command history. Hidden setting: read from
     * `client.toml` only, never written from the UI.
     */
    fun observeHistorySize(): Flow<Int> = clientConfigStore.observeClient().map { it.historySize ?: DEFAULT_HISTORY_SIZE }

    fun observeMarkLinks(): Flow<Boolean> = clientConfigStore.observeClient().map { it.markLinks }

    fun observeAutoConnectLastConnection(): Flow<Boolean> = clientConfigStore.observeClient().map { it.autoConnectLastConnection }

    fun getAutoConnectLastConnection(): Boolean = clientConfigStore.currentClient().autoConnectLastConnection

    suspend fun putAutoConnectLastConnection(value: Boolean) {
        clientConfigStore.mutateClient { it.copy(autoConnectLastConnection = value) }
    }

    /** How far the global default-macro set has been merged in; null on configs written before this existed. */
    suspend fun getMacroDefaultsVersion(): Int? = clientConfigStore.currentClient().macroDefaultsVersion

    suspend fun putMacroDefaultsVersion(value: Int) {
        clientConfigStore.mutateClient { it.copy(macroDefaultsVersion = value) }
    }

    // --- MUD Mobile device token ---

    fun observeMudMobileToken(): Flow<String?> = clientConfigStore.observeClient().map { it.mudMobileToken }

    fun getMudMobileToken(): String? = clientConfigStore.currentClient().mudMobileToken

    suspend fun putMudMobileToken(value: String?) {
        clientConfigStore.mutateClient { it.copy(mudMobileToken = value?.ifBlank { null }) }
    }

    fun observeShowImages(): Flow<Boolean> = clientConfigStore.observeClient().map { it.showImages }

    fun observeSuppressPrompts(): Flow<Boolean> = clientConfigStore.observeClient().map { it.suppressPrompts }

    suspend fun putLoggingPath(value: String) {
        clientConfigStore.mutateClient { it.copy(logPath = value) }
    }

    suspend fun putLoggingType(value: LogType) {
        clientConfigStore.mutateClient { it.copy(logType = value.name) }
    }

    suspend fun putLoggingTimestamps(value: Boolean) {
        clientConfigStore.mutateClient { it.copy(logTimestamps = value) }
    }

    suspend fun putTheme(value: ThemeSetting) {
        clientConfigStore.mutateClient { it.copy(theme = value.name) }
    }

    suspend fun putCompassStyle(value: CompassStyle) {
        clientConfigStore.mutateClient { it.copy(compassStyle = value.name) }
    }

    suspend fun putSkinFile(value: String?) {
        clientConfigStore.mutateClient { it.copy(skinFile = value) }
    }

    suspend fun putReleaseChannel(value: ReleaseChannelSetting) {
        clientConfigStore.mutateClient { it.copy(releaseChannel = value.name) }
    }

    suspend fun putMaxScrollLines(value: Int?) {
        clientConfigStore.mutateClient { it.copy(scrollback = value) }
    }

    suspend fun putMinCommandLength(value: Int?) {
        clientConfigStore.mutateClient { it.copy(minCommandLength = value) }
    }

    suspend fun putMarkLinks(value: Boolean) {
        clientConfigStore.mutateClient { it.copy(markLinks = value) }
    }

    suspend fun putShowImages(value: Boolean) {
        clientConfigStore.mutateClient { it.copy(showImages = value) }
    }

    suspend fun putSuppressPrompts(value: Boolean) {
        clientConfigStore.mutateClient { it.copy(suppressPrompts = value) }
    }

    // --- SQLite helpers ---

    private suspend fun get(key: String): String? = clientSettingDao.getByKey(key)

    private fun observe(key: String): Flow<String?> = clientSettingDao.observeByKey(key)

    private suspend fun getInt(key: String): Int? = get(key)?.toIntOrNull()

    private suspend fun getBoolean(key: String): Boolean? = get(key)?.toBoolean()

    private suspend fun putInt(
        key: String,
        value: Int,
    ) {
        put(key, value.toString())
    }

    private suspend fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        put(key, value.toString())
    }

    private suspend fun put(
        key: String,
        value: String?,
    ) {
        withContext(NonCancellable) {
            clientSettingDao.save(ClientSettingEntity(key, value))
        }
    }

    companion object {
        const val DEFAULT_MAX_SCROLL_LINES = 2_000
        const val DEFAULT_MIN_COMMAND_LENGTH = 3
        const val DEFAULT_HISTORY_SIZE = 1_000
        const val SCROLLBACK_KEY = "scrollback"
        const val MARK_LINKS_KEY = "markLinks"
        const val SHOW_IMAGES_KEY = "showImages"
        const val RELEASE_CHANNEL_KEY = "releaseChannel"
    }
}
