package warlockfe.warlock3.core.util

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.LineEvent
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.Mixer

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

            // null == the default clip; fall through to specific mixers if it won't open.
            val candidates: List<Mixer.Info?> = listOf(null) + AudioSystem.getMixerInfo()
            var lastError: Exception? = null

            for (mixer in candidates) {
                var clip: Clip? = null
                try {
                    clip = if (mixer != null) AudioSystem.getClip(mixer) else AudioSystem.getClip()
                    clip.addLineListener { event ->
                        if (event.type == LineEvent.Type.STOP) event.line.close()
                    }
                    AudioSystem.getAudioInputStream(file).use { input ->
                        clip.open(input)
                    }
                    clip.start()
                    logger.d { "playing on mixer = ${mixer?.name ?: "default"}, line = ${clip.lineInfo}" }
                    return@withContext null
                } catch (e: LineUnavailableException) {
                    lastError = e
                    clip?.close()
                } catch (e: Exception) {
                    clip?.close()
                    return@withContext e.message ?: "Error playing sound: $filename"
                }
            }
            lastError?.message ?: "No audio device could play $filename"
        }
}
