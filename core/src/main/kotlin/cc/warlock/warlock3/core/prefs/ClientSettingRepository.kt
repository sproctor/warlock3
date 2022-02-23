package cc.warlock.warlock3.core.prefs

import cc.warlock.warlock3.core.prefs.sql.ClientSetting
import cc.warlock.warlock3.core.prefs.sql.ClientSettingQueries
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

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

    fun observeScriptDirs(): Flow<List<String>> {
        return observe("scriptDirs").map {
            it?.split(",") ?: emptyList()
        }
    }

    suspend fun get(key: String): String? {
        return withContext(ioDispatcher) {
            clientSettingQueries.getByKey(key).executeAsOneOrNull()?.value_
        }
    }

    fun observe(key: String): Flow<String?> {
        return clientSettingQueries.getByKey(key)
            .asFlow()
            .mapToOneOrNull(ioDispatcher)
            .map { it?.value_ }
    }

    suspend fun getInt(key: String): Int? {
        return get(key)?.toIntOrNull()
    }

    suspend fun putWidth(value: Int) {
        putInt("width", value)
    }

    suspend fun putHeight(value: Int) {
        putInt("height", value)
    }

    suspend fun putScriptDirs(value: List<String>) {
        put("scriptDirs", value.joinToString(","))
    }

    suspend fun putInt(key: String, value: Int) {
        put(key, value.toString())
    }

    suspend fun put(key: String, value: String) {
        withContext(ioDispatcher) {
            clientSettingQueries.save(ClientSetting(key, value))
        }
    }
}