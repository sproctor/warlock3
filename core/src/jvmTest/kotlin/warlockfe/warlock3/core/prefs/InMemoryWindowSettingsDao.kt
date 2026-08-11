package warlockfe.warlock3.core.prefs

import kotlinx.coroutines.flow.Flow
import warlockfe.warlock3.core.prefs.dao.WindowSettingsDao
import warlockfe.warlock3.core.prefs.models.WindowSettingsEntity
import warlockfe.warlock3.core.text.WarlockColor

/**
 * In-memory [WindowSettingsDao] for the open/close paths; the styling methods are unused because
 * styling lives in the character config, not the database.
 */
class InMemoryWindowSettingsDao : WindowSettingsDao {
    private val rows = LinkedHashMap<Pair<String, String>, WindowSettingsEntity>()

    override fun observeByCharacter(characterId: String): Flow<List<WindowSettingsEntity>> = error("unused")

    override suspend fun getByCharacter(characterId: String): List<WindowSettingsEntity> =
        rows.values
            .filter { it.characterId == characterId }
            .sortedBy { it.name }

    override suspend fun getByName(
        characterId: String,
        name: String,
    ): WindowSettingsEntity? = rows[characterId to name]

    override suspend fun save(windowSettings: WindowSettingsEntity) {
        rows[windowSettings.characterId to windowSettings.name] = windowSettings
    }

    override suspend fun openWindow(
        characterId: String,
        name: String,
    ) {
        val existing = rows[characterId to name]
        rows[characterId to name] =
            existing?.copy(open = true)
                ?: windowSettingsEntity(characterId = characterId, name = name, open = true)
    }

    override suspend fun closeWindow(
        characterId: String,
        name: String,
    ) {
        rows[characterId to name]?.let { rows[characterId to name] = it.copy(open = false) }
    }

    override suspend fun deleteByCharacter(characterId: String) = error("unused")
}

/** An open-flag row: the vestigial styling columns keep their defaults. */
fun windowSettingsEntity(
    characterId: String,
    name: String,
    open: Boolean = false,
): WindowSettingsEntity =
    WindowSettingsEntity(
        characterId = characterId,
        name = name,
        open = open,
    )
