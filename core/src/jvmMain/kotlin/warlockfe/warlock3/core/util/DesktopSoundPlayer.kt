package warlockfe.warlock3.core.util

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineEvent
import javax.sound.sampled.Mixer
import javax.sound.sampled.SourceDataLine

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

    private val workingMixer: Mixer.Info? by lazy {
        val info = DataLine.Info(SourceDataLine::class.java, null)
        AudioSystem
            .getMixerInfo()
            .firstOrNull { mi ->
                val m = AudioSystem.getMixer(mi)
                m.isLineSupported(info) &&
                    runCatching {
                        m.getLine(info).apply {
                            open()
                            close()
                        }
                    }.isSuccess
            }.also {
                if (it == null) {
                    Logger.w { "Could not find a working mixer" }
                }
            }
    }

    override suspend fun playSound(filename: String): String? =
        withContext(Dispatchers.IO) {
            val file =
                File(filename).takeIf { it.exists() }
                    ?: dirs.map { File(it, filename) }.firstOrNull { it.exists() }
                    ?: return@withContext "File not found"
            try {
                val clip = workingMixer?.let { AudioSystem.getClip(it) } ?: AudioSystem.getClip()
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
