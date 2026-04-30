package warlockfe.warlock3.core.util

import java.io.File
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent

class DesktopSoundPlayer(
    warlockDirs: WarlockDirs
) : SoundPlayer {

    private val dirs = listOf(
        warlockDirs.dataDir,
        warlockDirs.configDir,
        warlockDirs.homeDir,
    )

    override fun playSound(filename: String): String? {
        val file = File(filename).takeIf { it.exists() }
            ?: dirs.map { File(it, filename) }.firstOrNull { it.exists() }
            ?: return "File not found"
        try {
            val clip = AudioSystem.getClip()
            clip.addLineListener { event ->
                if (event.type == LineEvent.Type.STOP) {
                    clip.close()
                }
            }
            AudioSystem.getAudioInputStream(file).use { inputStream ->
                clip.open(inputStream)
            }
            clip.start()
            return null
        } catch (e: Exception) {
            return e.message
        }
    }
}