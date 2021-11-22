package cc.warlock.warlock3.app.config

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.source.hocon
import com.uchuhimo.konf.source.hocon.toHocon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds

class ConfigWatcher {
    private val preferencesFileName = "preferences.conf"
    private val preferencesDirectory = System.getProperty("user.home") + "/.warlock3"
    private val preferencesFile = File("$preferencesDirectory/$preferencesFileName")

    private val preferencesPath = FileSystems.getDefault().getPath(preferencesDirectory)
    private val watchService = FileSystems.getDefault().newWatchService()
    private var watchKey = preferencesPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)

    val configState = MutableStateFlow(loadConfig())

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            while (true) {
                try {
                    val watchKey = watchService.take()
                    for (event in watchKey.pollEvents()) {
                        val changed = event.context() as Path
                        println("file changed: $changed")
                        if (changed.endsWith(preferencesFileName)) {
                            println("reloading config")
                            configState.value = loadConfig()
                        }
                    }
                    if (!watchKey.reset()) {
                        println("Key already unregistered")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // we can probably ignore this
                }
            }
        }
    }

    private fun loadConfig(): Config {
        if (!preferencesFile.exists()) {
            preferencesFile.parentFile.mkdirs()
            preferencesFile.createNewFile()
        }
        return Config {
            addSpec(SgeSpec)
            addSpec(ClientSpec)
        }
            .from.hocon.file(preferencesFile)
    }

    @Synchronized
    fun updateConfig(
        updates: (Config) -> Config
    ) {
        // first, lock file
        RandomAccessFile(preferencesFile, "rw").channel.use { channel ->
            channel.lock().use {
                // remove our watch listener
                watchKey.cancel()

                // load config from file in case we had changes we didn't find
                val config = loadConfig()

                // then apply the new update
                val newConfig = updates(config)

                // Then save the changes to preference
                newConfig.toHocon.toFile(preferencesFile)

                configState.value = newConfig

                // finally, restart watching the file
                watchKey = preferencesPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY)
            }
        }
    }
}