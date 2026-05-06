package warlockfe.warlock3.core.util

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent

class DesktopSoundPlayer(
    warlockDirs: WarlockDirs,
) : SoundPlayer {
    private val logger = Logger.withTag("DesktopSoundPlayer")

    private val dirs =
        listOf(
            warlockDirs.dataDir,
            warlockDirs.configDir,
            warlockDirs.homeDir,
        )

    override suspend fun playSound(filename: String): String? =
        withContext(Dispatchers.IO) {
            val file =
                File(filename).takeIf { it.exists() }
                    ?: dirs.map { File(it, filename) }.firstOrNull { it.exists() }
                    ?: return@withContext "File not found"
            try {
                logger.d { "default mixer = ${AudioSystem.getMixer(null).mixerInfo}" }
                AudioSystem.getMixerInfo().forEach { logger.d { "  $it" } }
                val clip = AudioSystem.getClip()
                logger.d { "clip line = ${clip.lineInfo}" }
                clip.addLineListener { event ->
                    if (event.type == LineEvent.Type.STOP) {
                        clip.close()
                    }
                }
                AudioSystem.getAudioInputStream(file).use { inputStream ->
                    clip.open(inputStream)
                }
                clip.start()
                null
            } catch (e: Exception) {
                e.message
            }
        }
}
