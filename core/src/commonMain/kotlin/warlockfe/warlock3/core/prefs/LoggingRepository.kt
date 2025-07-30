package warlockfe.warlock3.core.prefs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.Path
import warlockfe.warlock3.core.util.FileLogger
import warlockfe.warlock3.core.util.LogType

class LoggingRepository(
    clientSettingRepository: ClientSettingRepository,
    externalScope: CoroutineScope,
) {
    private val mutex = Mutex()

    private val loggingSettings = clientSettingRepository.observeLogSettings()
        .onEach {
            mutex.withLock {
                loggers.forEach { (_, logger) -> logger?.close() }
                loggers.clear()
            }
        }
        .stateIn(externalScope, SharingStarted.Eagerly, null)

    private val loggers = mutableMapOf<String, FileLogger?>()

    suspend fun logSimple(name: String, message: () -> String) {
        log(LogType.SIMPLE, name, message)
    }

    suspend fun logComplete(name: String, message: () -> String) {
        log(LogType.COMPLETE, name, message)
    }

    private suspend fun log(type: LogType, name: String, message: () -> String) {
        val settings = loggingSettings.value ?: error("Attempted to print log before settings were loaded")
        if (settings.type == type) {
            val logger = getLogger(settings.basePath, name)
            logger?.write(message(), settings.logTimestamps, settings.type)
        }
    }

    private suspend fun getLogger(path: String, name: String): FileLogger? {
        return mutex.withLock {
            loggers[name]?.let { return@withLock it }
            val directory = Path(path, name)
            val logger = FileLogger.getLogger(directory)
            logger.also { loggers[name] = it }
        }
    }
}
