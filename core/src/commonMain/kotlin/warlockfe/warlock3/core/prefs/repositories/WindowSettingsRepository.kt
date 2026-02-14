package warlockfe.warlock3.core.prefs.repositories

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import warlockfe.warlock3.core.prefs.dao.WindowSettingsDao
import warlockfe.warlock3.core.prefs.models.WindowSettingsEntity
import warlockfe.warlock3.core.text.StyleDefinition
import warlockfe.warlock3.core.window.WindowLocation

@OptIn(ExperimentalCoroutinesApi::class)
class WindowSettingsRepository(
    private val windowSettingsDao: WindowSettingsDao,
) {

    private val logger = KotlinLogging.logger {}

    fun observeWindowSettings(characterId: String): Flow<List<WindowSettingsEntity>> {
        return windowSettingsDao.observeByCharacter(characterId)
    }

    suspend fun openWindow(characterId: String, name: String, location: WindowLocation, position: Int) {
        logger.debug { "openWindow: $characterId, $name, $location, $position" }
        withContext(NonCancellable) {
            windowSettingsDao.openWindow(characterId, name, location, position)
        }
    }

    suspend fun closeWindow(characterId: String, name: String) {
        withContext(NonCancellable) {
            windowSettingsDao.closeWindow(characterId, name)
        }
    }

    suspend fun moveWindow(characterId: String, name: String, location: WindowLocation) {
        withContext(NonCancellable) {
            windowSettingsDao.moveWindow(characterId, name, location)
        }
    }

    suspend fun setWindowWidth(characterId: String, name: String, width: Int) {
        withContext(NonCancellable) {
            windowSettingsDao.updateWidth(characterId = characterId, name = name, width = width)
        }
    }

    suspend fun setWindowHeight(characterId: String, name: String, height: Int) {
        withContext(NonCancellable) {
            windowSettingsDao.updateHeight(characterId = characterId, name = name, height = height)
        }
    }

    suspend fun setStyle(characterId: String, name: String, style: StyleDefinition) {
        withContext(NonCancellable) {
            windowSettingsDao.setStyle(
                characterId = characterId,
                name = name,
                textColor = style.textColor,
                backgroundColor = style.backgroundColor,
                fontFamily = style.fontFamily,
                fontSize = style.fontSize,
            )
        }
    }

    suspend fun setPosition(characterId: String, name: String, pos: Int) {
        withContext(NonCancellable) {
            logger.debug { "setPosition: $characterId, $name, $pos" }
            windowSettingsDao.setPosition(characterId, name, pos)
        }
    }
}
