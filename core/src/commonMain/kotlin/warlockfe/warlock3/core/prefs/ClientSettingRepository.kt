package warlockfe.warlock3.core.prefs

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.dao.ClientSettingDao
import warlockfe.warlock3.core.prefs.models.ClientSettingEntity
import warlockfe.warlock3.core.util.LogSettings
import warlockfe.warlock3.core.util.LogType
import warlockfe.warlock3.core.util.WarlockDirs

class ClientSettingRepository(
    private val clientSettingDao: ClientSettingDao,
    private val warlockDirs: WarlockDirs,
) {
    suspend fun getWidth(): Int? {
        return getInt("width")
    }

    suspend fun getHeight(): Int? {
        return getInt("height")
    }

    suspend fun getIgnoreUpdates(): Boolean {
        return getBoolean("ignoreUpdates") ?: false
    }

    suspend fun getLastUsername(): String? {
        return get("lastUsername")
    }

    fun observeTheme(): Flow<ThemeSetting> {
        return observe("theme").map { if (it != null) ThemeSetting.valueOf(it) else ThemeSetting.AUTO }
    }

    fun observeLogSettings(): Flow<LogSettings> {
        return combine(
            observe("logPath"),
            observe("logType"),
            observe("logTimestamps"),
        ) { logPath, logType, logTimestamps ->
            LogSettings(
                basePath = logPath ?: warlockDirs.logDir,
                type = logType?.let { LogType.valueOf(it) } ?: LogType.SIMPLE,
                logTimestamps = logTimestamps?.toBooleanStrictOrNull() ?: true,
            )
        }
    }

    private suspend fun get(key: String): String? {
        return clientSettingDao.getByKey(key)
    }

    private fun observe(key: String): Flow<String?> {
        return clientSettingDao.observeByKey(key)
    }

    private suspend fun getInt(key: String): Int? {
        return get(key)?.toIntOrNull()
    }

    private suspend fun getFloat(key: String): Float? {
        return get(key)?.toFloatOrNull()
    }

    private suspend fun getBoolean(key: String): Boolean? {
        return get(key)?.toBoolean()
    }

    suspend fun putWidth(value: Int) {
        putInt("width", value)
    }

    suspend fun putHeight(value: Int) {
        putInt("height", value)
    }

    suspend fun putLoggingPath(value: String) {
        put("logPath", value)
    }

    suspend fun putLoggingType(value: LogType) {
        put("logType", value.name)
    }

    suspend fun putLoggingTimestamps(value: Boolean) {
        putBoolean("logTimestamps", value)
    }

    suspend fun putTheme(value: ThemeSetting) {
        put("theme", value.name)
    }

    suspend fun putLastUsername(value: String?) {
        put("lastUsername", value)
    }

    suspend fun putIgnoreUpdates(value: Boolean) {
        putBoolean("ignoreUpdates", value)
    }

    private suspend fun putInt(key: String, value: Int) {
        put(key, value.toString())
    }

    private suspend fun putFloat(key: String, value: Float) {
        put(key, value.toString())
    }

    private suspend fun putBoolean(key: String, value: Boolean) {
        put(key, value.toString())
    }

    private suspend fun put(key: String, value: String?) {
        withContext(NonCancellable) {
            clientSettingDao.save(ClientSettingEntity(key, value))
        }
    }
}
