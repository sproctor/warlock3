package warlockfe.warlock3.core.prefs

import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.sql.ClientSetting
import warlockfe.warlock3.core.prefs.sql.ClientSettingQueries
import warlockfe.warlock3.core.util.mapToOneOrNull

class ClientSettingRepository(
    private val clientSettingQueries: ClientSettingQueries,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun getWidth(): Int? {
        return getInt("width")
    }

    suspend fun getHeight(): Int? {
        return getInt("height")
    }

    suspend fun getScale(): Float? {
        return getFloat("scale")
    }

    suspend fun getIgnoreUpdates(): Boolean {
        return getBoolean("ignoreUpdates") ?: false
    }

    fun observeScale(): Flow<Float?> {
        return observe("scale").map { it?.toFloatOrNull() }
    }

    suspend fun get(key: String): String? {
        return withContext(ioDispatcher) {
            clientSettingQueries.getByKey(key).executeAsOneOrNull()?.value_
        }
    }

    private fun observe(key: String): Flow<String?> {
        return clientSettingQueries.getByKey(key)
            .asFlow()
            .mapToOneOrNull()
            .map { it?.value_ }
            .flowOn(ioDispatcher)
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

    suspend fun putScale(value: Float) {
        putFloat("scale", value)
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
        withContext(ioDispatcher) {
            clientSettingQueries.save(ClientSetting(key, value))
        }
    }
}