package warlockfe.warlock3.core.prefs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import okio.Path.Companion.toPath
import warlockfe.warlock3.core.util.FileLogger
import warlockfe.warlock3.core.util.LogType

class LoggingRepository(
    clientSettingRepository: ClientSettingRepository,
    externalScope: CoroutineScope,
) {
    private val loggingSettings = clientSettingRepository.observeLogSettings()
        .onEach {
            loggers.forEach { (_, logger) -> logger.close() }
            loggers.clear()
        }
        .stateIn(externalScope, SharingStarted.Eagerly, null)

    private val loggers = mutableMapOf<String, FileLogger>()

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
            logger.write(message(), settings.logTimestamps)
        }
    }

    private fun getLogger(path: String, name: String): FileLogger {
        loggers[name]?.let { return it }

        val directory = path.toPath() / name
        val logger = FileLogger(directory)
        return logger.also { loggers[name] = it }
    }
}

